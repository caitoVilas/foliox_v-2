package com.foliox.auth.user;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@Table("usuarios")
public class Usuario {

    @Id
    private UUID id;

    private String email;

    private String password;

    private String nombre;

    private String rol;

    private Boolean activo;
}
