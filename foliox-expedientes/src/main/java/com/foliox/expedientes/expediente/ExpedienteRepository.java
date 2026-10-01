package com.foliox.expedientes.expediente;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ExpedienteRepository extends ReactiveCrudRepository<Expediente, UUID> {

    Flux<Expediente> findByEstudioId(UUID estudioId);

    Mono<Expediente> findByIdAndEstudioId(UUID id, UUID estudioId);

    Flux<Expediente> findByEstudioIdAndFuero(UUID estudioId, String fuero);

    Flux<Expediente> findByEstudioIdAndEstado(UUID estudioId, String estado);

    Flux<Expediente> findByEstudioIdAndFueroAndEstado(UUID estudioId, String fuero, String estado);
}
