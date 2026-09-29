package com.foliox.usuarios.web;

import com.foliox.usuarios.estudio.CrearEstudioCommand;
import com.foliox.usuarios.estudio.EstudioConAdminDTO;
import com.foliox.usuarios.estudio.EstudioService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/estudios")
public class EstudioController {

    private final EstudioService estudioService;

    public EstudioController(EstudioService estudioService) {
        this.estudioService = estudioService;
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<EstudioConAdminDTO> crear(@Valid @RequestBody CrearEstudioCommand command) {
        return estudioService.crearEstudioConAdmin(command);
    }
}
