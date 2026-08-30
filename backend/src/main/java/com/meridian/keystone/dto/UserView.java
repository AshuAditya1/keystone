package com.meridian.keystone.dto;

import com.meridian.keystone.domain.Role;
import com.meridian.keystone.domain.User;

/**
 * Safe, client-facing projection of a {@link User}. Never exposes the password
 * hash. Returned by {@code /api/auth/login} and {@code /api/auth/me}.
 */
public record UserView(
        Long id,
        String email,
        String fullName,
        Role role,
        Long customerId) {

    public static UserView from(User user) {
        return new UserView(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getCustomer() == null ? null : user.getCustomer().getId());
    }
}
