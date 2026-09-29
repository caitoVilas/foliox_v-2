package com.foliox.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Credenciales para el grant password (RFC 6749 §4.3)")
public record TokenForm(
        @Schema(
                        description = "Tipo de grant. Solo se soporta `password`",
                        allowableValues = {"password"},
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        example = "password")
                String grant_type,
        @Schema(description = "Email del usuario", requiredMode = Schema.RequiredMode.REQUIRED, example = "test@foliox.dev")
                String username,
        @Schema(description = "Contraseña del usuario", requiredMode = Schema.RequiredMode.REQUIRED, example = "password")
                String password,
        @Schema(description = "Client id registrado", requiredMode = Schema.RequiredMode.REQUIRED, example = "foliox-web")
                String client_id,
        @Schema(description = "Client secret (omitir si el cliente es público)", example = "")
                String client_secret,
        @Schema(description = "Scope solicitado. Si se omite se aplica el default del cliente", example = "access")
                String scope) {
}
