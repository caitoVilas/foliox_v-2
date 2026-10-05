package com.foliox.documentos.documento;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DocumentoRepository extends ReactiveCrudRepository<Documento, UUID> {

    Flux<Documento> findByEstudioId(UUID estudioId);

    Mono<Documento> findByIdAndEstudioId(UUID id, UUID estudioId);
}
