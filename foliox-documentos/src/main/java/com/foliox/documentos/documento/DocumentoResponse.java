package com.foliox.documentos.documento;

import java.time.Instant;
import java.util.UUID;

public record DocumentoResponse(
        UUID id, UUID estudioId, UUID expedienteId, String nombre, String ubicacion, Instant creadoEn) {

    public static DocumentoResponse from(Documento documento) {
        return new DocumentoResponse(
                documento.getId(),
                documento.getEstudioId(),
                documento.getExpedienteId(),
                documento.getNombre(),
                documento.getUbicacion(),
                documento.getCreadoEn());
    }
}
