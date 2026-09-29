package com.foliox.usuarios.estudio;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearEstudioCommand(
        @NotBlank(message = "el nombre del estudio es obligatorio")
                String nombreEstudio,
        @NotBlank(message = "el nombre es obligatorio")
                String nombre,
        @NotBlank(message = "el email es obligatorio")
                @Email(message = "el email no es válido")
                String email,
        @NotBlank(message = "la contraseña es obligatoria")
                @Size(min = 8, message = "la contraseña debe tener al menos 8 caracteres")
                String password) {
}
