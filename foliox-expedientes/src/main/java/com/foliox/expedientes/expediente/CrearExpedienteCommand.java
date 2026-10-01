package com.foliox.expedientes.expediente;

import com.foliox.common.enums.EstadoExpediente;
import com.foliox.common.enums.Fuero;

import jakarta.validation.constraints.NotNull;

public record CrearExpedienteCommand(
        @NotNull(message = "el nombre es obligatorio") String nombre,
        @NotNull(message = "el fuero es obligatorio") Fuero fuero, EstadoExpediente estado) {
}
