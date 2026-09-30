package com.foliox.usuarios.web;

import java.util.UUID;

import com.foliox.usuarios.usuario.ActualizarUsuarioCommand;
import com.foliox.usuarios.usuario.CrearUsuarioCommand;
import com.foliox.usuarios.usuario.UsuarioResponse;
import com.foliox.usuarios.usuario.UsuarioService;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuarios", description = "CRUD de usuarios del estudio (multi-tenant)")
@SecurityRequirement(name = "bearerAuth")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<UsuarioResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return usuarioService.obtenerPropio(currentUserId(jwt));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public Mono<java.util.List<UsuarioResponse>> listar(@AuthenticationPrincipal Jwt jwt) {
        return usuarioService.listar(estudioId(jwt));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<UsuarioResponse> obtener(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return usuarioService.obtener(id, estudioId(jwt), currentUserId(jwt), isAdmin(jwt));
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public Mono<UsuarioResponse> crear(
            @Valid @RequestBody CrearUsuarioCommand command, @AuthenticationPrincipal Jwt jwt) {
        return usuarioService.crear(estudioId(jwt), command);
    }

    @PatchMapping(
            value = "/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<UsuarioResponse> actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody ActualizarUsuarioCommand command,
            @AuthenticationPrincipal Jwt jwt) {
        return usuarioService.actualizar(
                id, estudioId(jwt), currentUserId(jwt), isAdmin(jwt), command);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public Mono<Void> eliminar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return usuarioService.eliminar(id, estudioId(jwt), currentUserId(jwt));
    }

    private static UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static UUID estudioId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("estudio_id"));
    }

    private static boolean isAdmin(Jwt jwt) {
        return "ADMIN".equals(jwt.getClaimAsString("rol"));
    }
}
