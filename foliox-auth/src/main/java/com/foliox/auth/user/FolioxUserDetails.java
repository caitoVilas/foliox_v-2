package com.foliox.auth.user;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.foliox.common.enums.Rol;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class FolioxUserDetails implements UserDetails {

    private final UUID id;
    private final String email;
    private final String password;
    private final String nombre;
    private final Rol rol;
    private final boolean activo;

    public FolioxUserDetails(UUID id, String email, String password, String nombre, Rol rol, boolean activo) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.nombre = nombre;
        this.rol = rol;
        this.activo = activo;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getNombre() {
        return nombre;
    }

    public Rol getRol() {
        return rol;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return activo;
    }
}
