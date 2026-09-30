package com.foliox.usuarios.usuario;

import java.util.UUID;

import com.foliox.common.exception.ForbiddenOperationException;
import com.foliox.common.exception.ResourceNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Mono<UsuarioResponse> obtenerPropio(UUID usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Usuario no encontrado")))
                .map(UsuarioResponse::from);
    }

    public Mono<java.util.List<UsuarioResponse>> listar(UUID estudioId) {
        return usuarioRepository.findByEstudioId(estudioId)
                .map(UsuarioResponse::from)
                .collectList();
    }

    public Mono<UsuarioResponse> obtener(UUID id, UUID estudioId, UUID currentUserId, boolean admin) {
        return autorizar(id, estudioId, currentUserId, admin).map(UsuarioResponse::from);
    }

    @Transactional
    public Mono<UsuarioResponse> crear(UUID estudioId, CrearUsuarioCommand command) {
        return usuarioRepository.findByEmail(command.email())
                .flatMap(existing -> Mono.<UsuarioResponse>error(emailDuplicado()))
                .switchIfEmpty(Mono.defer(() -> {
                    Usuario usuario = new Usuario();
                    usuario.setNombre(command.nombre());
                    usuario.setEmail(command.email());
                    usuario.setPassword(passwordEncoder.encode(command.password()));
                    usuario.setRol(command.rol().name());
                    usuario.setActivo(Boolean.TRUE);
                    usuario.setEstudioId(estudioId);
                    return usuarioRepository.save(usuario).map(UsuarioResponse::from);
                }));
    }

    @Transactional
    public Mono<UsuarioResponse> actualizar(
            UUID id, UUID estudioId, UUID currentUserId, boolean admin, ActualizarUsuarioCommand command) {
        return autorizar(id, estudioId, currentUserId, admin)
                .flatMap(usuario -> {
                    aplicarCambios(usuario, command, admin);
                    return usuarioRepository.save(usuario);
                })
                .map(UsuarioResponse::from);
    }

    @Transactional
    public Mono<Void> eliminar(UUID id, UUID estudioId, UUID currentUserId) {
        if (id.equals(currentUserId)) {
            return Mono.error(new ForbiddenOperationException("No podés eliminar tu propio usuario"));
        }
        return usuarioRepository.findById(id)
                .filter(usuario -> estudioId.equals(usuario.getEstudioId()))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Usuario no encontrado")))
                .flatMap(usuario -> usuarioRepository.delete(usuario));
    }

    private Mono<Usuario> autorizar(UUID id, UUID estudioId, UUID currentUserId, boolean admin) {
        return usuarioRepository.findById(id)
                .filter(usuario -> estudioId.equals(usuario.getEstudioId()))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Usuario no encontrado")))
                .flatMap(usuario -> {
                    if (admin || usuario.getId().equals(currentUserId)) {
                        return Mono.just(usuario);
                    }
                    return Mono.error(new ForbiddenOperationException(
                            "No tenés permiso sobre este usuario"));
                });
    }

    private void aplicarCambios(Usuario usuario, ActualizarUsuarioCommand command, boolean admin) {
        if (command.nombre() != null && !command.nombre().isBlank()) {
            usuario.setNombre(command.nombre());
        }
        if (command.password() != null && !command.password().isBlank()) {
            usuario.setPassword(passwordEncoder.encode(command.password()));
        }
        if (command.rol() != null && !command.rol().name().equals(usuario.getRol())) {
            if (!admin) {
                throw new ForbiddenOperationException("Solo un administrador puede cambiar el rol");
            }
            usuario.setRol(command.rol().name());
        }
    }

    private ResponseStatusException emailDuplicado() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "El email ya está registrado");
    }
}
