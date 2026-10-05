package com.foliox.documentos.client;

import java.util.UUID;

/** Minimal projection of expedientes {@code GET /api/expedientes/{id}}; {@code estado} compared as String. */
public record ExpedienteResumen(UUID id, UUID estudioId, String estado) {
}
