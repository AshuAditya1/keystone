package com.meridian.keystone.dto;

import com.meridian.keystone.domain.Role;
import com.meridian.keystone.domain.User;

/**
 * User as shown in admin lists and assignment pickers. Deliberately has no
 * password field of any kind.
 */
public record UserSummary(
        Long id,
        String email,
        String fullName,
        Role role,
        Long customerId,
        String customerName,
        boolean active) {

    public static UserSummary from(User user) {
        return new UserSummary(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getCustomer() == null ? null : user.getCustomer().getId(),
                user.getCustomer() == null ? null : user.getCustomer().getName(),
                user.isActive());
    }
}
