package com.foliox.documentos.client;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import com.foliox.common.exception.ResourceNotFoundException;
import com.foliox.documentos.exception.ServicioNoDisponibleException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;

/**
 * Decision 3: first inter-service HTTP call of the repo. The controller reads the raw
 * {@code Authorization} header and relays it explicitly ({@code controller → service → client →
 * .header(AUTHORIZATION, token)}) — grep-able and testable, no Reactor Context coupling.
 * Timeout pinned on the Mono inside the client (bounds connect + read in one place).
 * Mapping: timeout / connection failure / unexpected status → {@link ServicioNoDisponibleException}
 * (502); upstream 404 → {@link ResourceNotFoundException} (404 propagates). Never fail open.
 */
@Component
public class ExpedienteClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;

    public ExpedienteClient(WebClient.Builder builder, @Value("${app.expedientes.base-url}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    public Mono<ExpedienteResumen> obtener(UUID expedienteId, String token) {
        return webClient.get()
                .uri("/api/expedientes/{id}", expedienteId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .retrieve()
                .bodyToMono(ExpedienteResumen.class)
                .timeout(TIMEOUT)
                .onErrorMap(ExpedienteClient::mapearError);
    }

    private static Throwable mapearError(Throwable e) {
        if (e instanceof WebClientResponseException respuesta) {
            if (respuesta.getStatusCode().value() == 404) {
                return new ResourceNotFoundException("Expediente no encontrado");
            }
            return new ServicioNoDisponibleException(
                    "El servicio de expedientes devolvió una respuesta inesperada", e);
        }
        if (e instanceof TimeoutException || e instanceof WebClientRequestException) {
            return new ServicioNoDisponibleException("El servicio de expedientes no está disponible", e);
        }
        return new ServicioNoDisponibleException("No se pudo consultar el servicio de expedientes", e);
    }
}
