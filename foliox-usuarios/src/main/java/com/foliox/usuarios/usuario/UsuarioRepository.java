package com.foliox.usuarios.usuario;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UsuarioRepository extends ReactiveCrudRepository<Usuario, UUID> {

    Mono<Usuario> findByEmail(String email);

    Flux<Usuario> findByEstudioId(UUID estudioId);
}
