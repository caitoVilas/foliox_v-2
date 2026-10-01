package com.foliox.expedientes.web;

import java.util.List;
import java.util.UUID;

import com.foliox.common.enums.EstadoExpediente;
import com.foliox.common.enums.Fuero;
import com.foliox.expedientes.expediente.ActualizarExpedienteCommand;
import com.foliox.expedientes.expediente.CrearExpedienteCommand;
import com.foliox.expedientes.expediente.ExpedienteResponse;
import com.foliox.expedientes.expediente.ExpedienteService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/expedientes")
@Tag(name = "Expedientes", description = "CRUD de expedientes del estudio (multi-tenant)")
@SecurityRequirement(name = "bearerAuth")
public class ExpedienteController {

    private final ExpedienteService expedienteService;

    public ExpedienteController(ExpedienteService expedienteService) {
        this.expedienteService = expedienteService;
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ExpedienteResponse> crear(
            @Valid @RequestBody CrearExpedienteCommand command, @AuthenticationPrincipal Jwt jwt) {
        return expedienteService.crear(estudioId(jwt), command);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<List<ExpedienteResponse>> listar(
            @RequestParam(name = "fuero", required = false) Fuero fuero,
            @RequestParam(name = "estado", required = false) EstadoExpediente estado,
            @AuthenticationPrincipal Jwt jwt) {
        return expedienteService.listar(estudioId(jwt), fuero, estado);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ExpedienteResponse> obtener(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return expedienteService.obtener(id, estudioId(jwt));
    }

    @PatchMapping(
            value = "/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ExpedienteResponse> actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody ActualizarExpedienteCommand command,
            @AuthenticationPrincipal Jwt jwt) {
        return expedienteService.actualizar(id, estudioId(jwt), command);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> eliminar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return expedienteService.eliminar(id, estudioId(jwt), isAdmin(jwt));
    }

    /** Tenancy comes only from the JWT claim — body {@code estudio_id} is ignored. */
    private static UUID estudioId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("estudio_id"));
    }

    private static boolean isAdmin(Jwt jwt) {
        return "ADMIN".equals(jwt.getClaimAsString("rol"));
    }
}
