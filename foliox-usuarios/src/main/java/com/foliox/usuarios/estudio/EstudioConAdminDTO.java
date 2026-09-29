package com.foliox.usuarios.estudio;

import java.util.UUID;

import com.foliox.usuarios.usuario.Usuario;

import reactor.core.publisher.Mono;

public record EstudioConAdminDTO(UUID estudioId, UUID usuarioId) {

    public static EstudioConAdminDTO of(Estudio estudio, Usuario admin) {
        return new EstudioConAdminDTO(estudio.getId(), admin.getId());
    }
}
