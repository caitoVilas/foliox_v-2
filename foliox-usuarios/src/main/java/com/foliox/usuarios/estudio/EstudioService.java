package com.foliox.usuarios.estudio;

import com.foliox.common.enums.Rol;
import com.foliox.usuarios.usuario.Usuario;
import com.foliox.usuarios.usuario.UsuarioRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

@Service
public class EstudioService {

    private final EstudioRepository estudioRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public EstudioService(
            EstudioRepository estudioRepository,
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder) {
        this.estudioRepository = estudioRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Mono<EstudioConAdminDTO> crearEstudioConAdmin(CrearEstudioCommand command) {
        return usuarioRepository.findByEmail(command.email())
                .flatMap(existing -> Mono.<EstudioConAdminDTO>error(emailDuplicado()))
                .switchIfEmpty(Mono.defer(() -> crear(command)));
    }

    private Mono<EstudioConAdminDTO> crear(CrearEstudioCommand command) {
        Estudio estudio = new Estudio();
        estudio.setNombre(command.nombreEstudio());

        return estudioRepository.save(estudio)
                .flatMap(savedEstudio -> usuarioRepository.save(admin(command, savedEstudio))
                        .map(savedAdmin -> EstudioConAdminDTO.of(savedEstudio, savedAdmin)));
    }

    private Usuario admin(CrearEstudioCommand command, Estudio estudio) {
        Usuario admin = new Usuario();
        admin.setEmail(command.email());
        admin.setPassword(passwordEncoder.encode(command.password()));
        admin.setNombre(command.nombre());
        admin.setRol(Rol.ADMIN.name());
        admin.setActivo(Boolean.TRUE);
        admin.setEstudioId(estudio.getId());
        return admin;
    }

    private ResponseStatusException emailDuplicado() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "El email ya está registrado");
    }
}
