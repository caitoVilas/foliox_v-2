package com.foliox.auth.web;

import java.time.Instant;

import com.foliox.auth.config.AuthProperties;
import com.foliox.auth.user.FolioxUserDetails;

import jakarta.validation.Valid;

import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final ReactiveAuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final AuthProperties authProperties;

    public AuthController(
            ReactiveAuthenticationManager authenticationManager,
            JwtEncoder jwtEncoder,
            AuthProperties authProperties) {
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.authProperties = authProperties;
    }

    @PostMapping("/login")
    public Mono<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return authenticationManager
                .authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()))
                .map(authentication -> (FolioxUserDetails) authentication.getPrincipal())
                .map(this::issueToken);
    }

    private LoginResponse issueToken(FolioxUserDetails user) {
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
        return new LoginResponse(jwt.getTokenValue(), "Bearer", authProperties.tokenTtl().toSeconds());
    }
}
