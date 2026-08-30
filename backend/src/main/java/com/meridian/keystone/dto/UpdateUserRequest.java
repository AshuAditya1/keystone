package com.meridian.keystone.dto;

import com.meridian.keystone.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 150)
        String fullName,

        @NotNull(message = "Role is required")
        Role role,

        @NotNull(message = "Active flag is required")
        Boolean active,

        Long customerId,

        /** Optional: when present, the password is reset to this value. */
        @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
        String password) {
}
