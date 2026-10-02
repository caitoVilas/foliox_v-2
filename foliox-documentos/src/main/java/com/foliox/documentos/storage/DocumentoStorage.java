package com.foliox.documentos.storage;

import java.io.InputStream;

import reactor.core.publisher.Mono;

/**
 * Decision 2: storage seam behind MinIO. {@code subscribeOn(boundedElastic)} is applied ONLY in the
 * implementation — the MinIO SDK performs blocking stream I/O even behind its async facade.
 */
public interface DocumentoStorage {

    /** Stores the bytes under {@code clave}. Failure → {@code ServicioNoDisponibleException} (502). */
    Mono<Void> guardar(String clave, byte[] contenido);

    /**
     * Opens the object stream for {@code clave}. Empty Mono = object missing (caller decides —
     * a row-backed lookup maps it to 502, never 404).
     */
    Mono<InputStream> obtener(String clave);

    /** Best-effort delete; callers that must not fail the request log failures themselves. */
    Mono<Void> eliminar(String clave);
}
