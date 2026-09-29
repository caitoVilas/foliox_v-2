package com.foliox.auth.web;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import com.foliox.auth.config.AuthProperties;
import com.foliox.auth.user.FolioxUserDetails;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@RestController
public class TokenController {

    private static final String PASSWORD_GRANT_TYPE = "password";

    private final ReactiveAuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final AuthProperties authProperties;

    public TokenController(
            ReactiveAuthenticationManager authenticationManager,
            JwtEncoder jwtEncoder,
            AuthProperties authProperties) {
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.authProperties = authProperties;
    }

    @io.swagger.v3.oas.annotations.Operation(
            summary = "Obtener un access token (ROPC password grant)",
            description = "Autentica usuario y contraseña y devuelve un JWT de 2 horas. "
                    + "Errores RFC 6749 §5.2: 400 invalid_grant / unsupported_grant_type, 401 invalid_client.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @io.swagger.v3.oas.annotations.media.Content(
                    mediaType = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = TokenForm.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Token emitido",
            content = @io.swagger.v3.oas.annotations.media.Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = TokenResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "invalid_grant / unsupported_grant_type / invalid_request / invalid_scope",
            content = @io.swagger.v3.oas.annotations.media.Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = OAuthError.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "invalid_client",
            content = @io.swagger.v3.oas.annotations.media.Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = OAuthError.class)))
    @PostMapping(
            value = "/oauth2/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<?>> token(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) ServerWebExchange exchange) {
        return exchange.getFormData().flatMap(form -> {
            String grantType = form.getFirst("grant_type");
            String clientId = form.getFirst("client_id");
            String clientSecret = form.getFirst("client_secret");
            String username = form.getFirst("username");
            String password = form.getFirst("password");
            String scope = form.getFirst("scope");

            if (!PASSWORD_GRANT_TYPE.equals(grantType)) {
                return error(HttpStatus.BAD_REQUEST, "unsupported_grant_type",
                        "Solo se soporta grant_type=password");
            }
            if (!clientMatches(clientId, clientSecret)) {
                return error(HttpStatus.UNAUTHORIZED, "invalid_client",
                        "Cliente invalido o secreto invalido");
            }
            if (isBlank(username) || isBlank(password)) {
                return error(HttpStatus.BAD_REQUEST, "invalid_request",
                        "username y password son obligatorios");
            }
            String resolvedScope = resolveScope(scope);
            if (resolvedScope == null) {
                return error(HttpStatus.BAD_REQUEST, "invalid_scope", "Scope no soportado");
            }

            return authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(username, password))
                    .map(authentication -> (FolioxUserDetails) authentication.getPrincipal())
                    .<ResponseEntity<?>>map(user -> ResponseEntity.ok()
                            .cacheControl(CacheControl.noStore())
                            .body(issueToken(user, resolvedScope)))
                    .onErrorResume(AuthenticationException.class, ex ->
                            error(HttpStatus.BAD_REQUEST, "invalid_grant", "Credenciales invalidas"));
        });
    }

    private boolean clientMatches(String clientId, String clientSecret) {
        AuthProperties.Client client = authProperties.client();
        if (client == null || isBlank(client.id()) || !client.id().equals(clientId)) {
            return false;
        }
        if (isBlank(client.secret())) {
            return true;
        }
        return client.secret().equals(clientSecret);
    }

    private String resolveScope(String requestedScope) {
        AuthProperties.Client client = authProperties.client();
        String defaultScope = client == null || client.defaultScope() == null
                ? ""
                : client.defaultScope().trim();
        if (isBlank(requestedScope)) {
            return defaultScope;
        }
        Set<String> allowed = Arrays.stream(defaultScope.split("\\s+"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        for (String scope : requestedScope.trim().split("\\s+")) {
            if (!allowed.contains(scope)) {
                return null;
            }
        }
        return requestedScope.trim();
    }

    private TokenResponse issueToken(FolioxUserDetails user, String scope) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(authProperties.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(authProperties.tokenTtl()))
                .claim("email", user.getEmail())
                .claim("nombre", user.getNombre())
                .claim("rol", user.getRol().name())
                .build();
        var jwt = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(),
                claims));
        return new TokenResponse(
                jwt.getTokenValue(),
                "Bearer",
                authProperties.tokenTtl().toSeconds(),
                scope);
    }

    private Mono<ResponseEntity<?>> error(HttpStatus status, String error, String description) {
        return Mono.just(ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new OAuthError(error, description)));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
