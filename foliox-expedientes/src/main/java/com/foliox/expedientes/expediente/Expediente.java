package com.foliox.expedientes.expediente;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@Table("expedientes")
public class Expediente {

    @Id
    private UUID id;

    private UUID estudioId;

    private String nombre;

    /** {@code Fuero.name()} — stored as String (see design decision 2). */
    private String fuero;

    /** {@code EstadoExpediente.name()} — stored as String (see design decision 2). */
    private String estado;

    private Instant creadoEn;
}
