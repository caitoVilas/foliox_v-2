package com.foliox.auth.user;

import com.foliox.common.enums.Rol;

import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
public class FolioxUserDetailsService implements ReactiveUserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public FolioxUserDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        return usuarioRepository.findByEmail(username)
                .switchIfEmpty(Mono.error(() -> new UsernameNotFoundException(username)))
                .map(usuario -> new FolioxUserDetails(
                        usuario.getId(),
                        usuario.getEmail(),
                        usuario.getPassword(),
                        usuario.getNombre(),
                        Rol.valueOf(usuario.getRol()),
                        Boolean.TRUE.equals(usuario.getActivo())));
    }
}
