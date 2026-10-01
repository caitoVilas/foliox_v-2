package com.foliox.expedientes.expediente;

import java.time.Instant;
import java.util.UUID;

import com.foliox.common.enums.EstadoExpediente;
import com.foliox.common.enums.Fuero;

public record ExpedienteResponse(
        UUID id, UUID estudioId, String nombre, Fuero fuero, EstadoExpediente estado, Instant creadoEn) {

    public static ExpedienteResponse from(Expediente expediente) {
        return new ExpedienteResponse(
                expediente.getId(),
                expediente.getEstudioId(),
                expediente.getNombre(),
                Fuero.valueOf(expediente.getFuero()),
                EstadoExpediente.valueOf(expediente.getEstado()),
                expediente.getCreadoEn());
    }
}
