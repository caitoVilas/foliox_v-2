package com.foliox.expedientes.expediente;

import com.foliox.common.enums.EstadoExpediente;
import com.foliox.common.enums.Fuero;

public record ActualizarExpedienteCommand(String nombre, Fuero fuero, EstadoExpediente estado) {
}
