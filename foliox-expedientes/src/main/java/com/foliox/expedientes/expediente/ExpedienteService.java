package com.foliox.expedientes.expediente;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.foliox.common.enums.EstadoExpediente;
import com.foliox.common.enums.Fuero;
import com.foliox.common.exception.ForbiddenOperationException;
import com.foliox.common.exception.ResourceNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ExpedienteService {

    private final ExpedienteRepository expedienteRepository;

    public ExpedienteService(ExpedienteRepository expedienteRepository) {
        this.expedienteRepository = expedienteRepository;
    }

    @Transactional
    public Mono<ExpedienteResponse> crear(UUID estudioId, CrearExpedienteCommand command) {
        Expediente expediente = new Expediente();
        expediente.setEstudioId(estudioId);
        expediente.setNombre(command.nombre());
        expediente.setFuero(command.fuero().name());
        expediente.setEstado(
                command.estado() != null ? command.estado().name() : EstadoExpediente.INGRESADO.name());
        expediente.setCreadoEn(Instant.now());
        return expedienteRepository.save(expediente).map(ExpedienteResponse::from);
    }

    public Mono<List<ExpedienteResponse>> listar(UUID estudioId, Fuero fuero, EstadoExpediente estado) {
        return filtrar(estudioId, fuero, estado)
                .map(ExpedienteResponse::from)
                .collectList();
    }

    public Mono<ExpedienteResponse> obtener(UUID id, UUID estudioId) {
        return porEstudio(id, estudioId).map(ExpedienteResponse::from);
    }

    @Transactional
    public Mono<ExpedienteResponse> actualizar(
            UUID id, UUID estudioId, ActualizarExpedienteCommand command) {
        return porEstudio(id, estudioId)
                .flatMap(expediente -> {
                    if (command.nombre() != null) {
                        expediente.setNombre(command.nombre());
                    }
                    if (command.fuero() != null) {
                        expediente.setFuero(command.fuero().name());
                    }
                    if (command.estado() != null) {
                        expediente.setEstado(command.estado().name());
                    }
                    return expedienteRepository.save(expediente);
                })
                .map(ExpedienteResponse::from);
    }

    @Transactional
    public Mono<Void> eliminar(UUID id, UUID estudioId, boolean admin) {
        return porEstudio(id, estudioId)
                .flatMap(expediente -> {
                    if (!admin) {
                        return Mono.error(new ForbiddenOperationException(
                                "Solo un administrador puede eliminar expedientes"));
                    }
                    return expedienteRepository.delete(expediente);
                });
    }

    /** Scoped lookup: unknown or cross-estudio id → 404 (always before any 403 check). */
    private Mono<Expediente> porEstudio(UUID id, UUID estudioId) {
        return expedienteRepository.findByIdAndEstudioId(id, estudioId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Expediente no encontrado")));
    }

    private Flux<Expediente> filtrar(UUID estudioId, Fuero fuero, EstadoExpediente estado) {
        if (fuero != null && estado != null) {
            return expedienteRepository.findByEstudioIdAndFueroAndEstado(
                    estudioId, fuero.name(), estado.name());
        }
        if (fuero != null) {
            return expedienteRepository.findByEstudioIdAndFuero(estudioId, fuero.name());
        }
        if (estado != null) {
            return expedienteRepository.findByEstudioIdAndEstado(estudioId, estado.name());
        }
        return expedienteRepository.findByEstudioId(estudioId);
    }
}
