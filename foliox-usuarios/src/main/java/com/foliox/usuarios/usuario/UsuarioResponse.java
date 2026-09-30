package com.foliox.usuarios.usuario;

import java.util.UUID;

public record UsuarioResponse(
        UUID id, String email, String nombre, String rol, Boolean activo, UUID estudioId) {

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombre(),
                usuario.getRol(),
                usuario.getActivo(),
                usuario.getEstudioId());
    }
}
