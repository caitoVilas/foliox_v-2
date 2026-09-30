package com.foliox.usuarios;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "FolioX Usuarios", version = "v1"))
@SecurityScheme(name = "bearerAuth", type = io.swagger.v3.oas.annotations.enums.SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class UsuariosApplication {

    public static void main(String[] args) {
        SpringApplication.run(UsuariosApplication.class, args);
    }
}