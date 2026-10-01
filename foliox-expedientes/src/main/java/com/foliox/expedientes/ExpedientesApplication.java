package com.foliox.expedientes;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "FolioX Expedientes", version = "v1"))
@SecurityScheme(name = "bearerAuth", type = io.swagger.v3.oas.annotations.enums.SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class ExpedientesApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpedientesApplication.class, args);
    }
}
