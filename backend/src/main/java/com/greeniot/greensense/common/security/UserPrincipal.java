package com.greeniot.greensense.common.security;

import com.greeniot.greensense.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** The authenticated caller, available to controls via {@link SecurityUtils}. */
@Getter
public class UserPrincipal implements UserDetails {

    private final String id;
    private final String email;
    private final String role;
    private final String passwordHash;
    private final boolean enabled;

    public UserPrincipal(String id, String email, String role, String passwordHash, boolean enabled) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
    }

    public static UserPrincipal of(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getPasswordHash(),
                user.isEnabled());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
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
        return enabled;
    }
}
