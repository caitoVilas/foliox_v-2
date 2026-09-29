package com.foliox.auth.web;

import java.util.List;
import java.util.Map;

import com.foliox.auth.config.AuthProperties;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServerMetadataController {

    private final AuthProperties authProperties;

    public ServerMetadataController(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @GetMapping(
            value = "/.well-known/oauth-authorization-server",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> metadata() {
        String issuer = authProperties.issuer();
        AuthProperties.Client client = authProperties.client();

        boolean publicClient = client == null || isBlank(client.secret());
        List<String> authMethods = publicClient
                ? List.of("none")
                : List.of("client_secret_post");
        List<String> scopes = client == null || isBlank(client.defaultScope())
                ? List.of()
                : List.of(client.defaultScope().trim().split("\\s+"));

        return Map.of(
                "issuer", issuer,
                "token_endpoint", issuer + "/oauth2/token",
                "jwks_uri", issuer + "/oauth2/jwks",
                "response_types_supported", List.of(),
                "grant_types_supported", List.of("password"),
                "token_endpoint_auth_methods_supported", authMethods,
                "scopes_supported", scopes);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
