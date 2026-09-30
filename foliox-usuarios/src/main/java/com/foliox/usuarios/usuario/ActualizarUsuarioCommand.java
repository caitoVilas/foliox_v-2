package com.foliox.usuarios.usuario;

import com.foliox.common.enums.Rol;

import jakarta.validation.constraints.Size;

public record ActualizarUsuarioCommand(
        String nombre,
        @Size(min = 8, message = "la contraseña debe tener al menos 8 caracteres")
                String password,
        Rol rol) {
}
