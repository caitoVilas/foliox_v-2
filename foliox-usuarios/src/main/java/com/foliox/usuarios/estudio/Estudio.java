package com.foliox.usuarios.estudio;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@Table("estudios")
public class Estudio {

    @Id
    private UUID id;

    private String nombre;

    private Instant creadoEn;
}
