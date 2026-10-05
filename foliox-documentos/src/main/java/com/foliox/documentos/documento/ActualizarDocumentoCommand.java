package com.foliox.documentos.documento;

import java.util.UUID;

/**
 * Decision 8: only {@code nombre} and {@code expedienteId} are mutable; {@code null} = field absent
 * (disassociation is impossible — the spec never offers it). {@code estudio_id} is deliberately not
 * part of the record: tenancy derives only from the JWT and an unknown JSON property is ignored.
 */
public record ActualizarDocumentoCommand(String nombre, UUID expedienteId) {
}
