package com.foliox.usuarios.estudio;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface EstudioRepository extends ReactiveCrudRepository<Estudio, UUID> {
}
