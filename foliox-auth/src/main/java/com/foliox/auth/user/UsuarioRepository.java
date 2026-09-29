package com.foliox.auth.user;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Mono;

public interface UsuarioRepository extends ReactiveCrudRepository<Usuario, UUID> {

    Mono<Usuario> findByEmail(String email);
}
