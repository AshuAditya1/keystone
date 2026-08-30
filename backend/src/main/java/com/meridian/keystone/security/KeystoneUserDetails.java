package com.meridian.keystone.security;

import com.meridian.keystone.domain.Role;
import com.meridian.keystone.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adapts a {@link User} entity to Spring Security's {@link UserDetails}.
 * The single role becomes a {@code ROLE_<NAME>} authority so that
 * {@code hasRole('MANAGER')} / {@code @PreAuthorize} work as expected.
 */
public class KeystoneUserDetails implements UserDetails {

    private final User user;

    public KeystoneUserDetails(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    public Long getId() {
        return user.getId();
    }

    public Role getRole() {
        return user.getRole();
    }

    public String getFullName() {
        return user.getFullName();
    }

    /** Owning customer id for CUSTOMER-role users; null for staff. */
    public Long getCustomerId() {
        return user.getCustomer() == null ? null : user.getCustomer().getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
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
        return user.isActive();
    }
}
