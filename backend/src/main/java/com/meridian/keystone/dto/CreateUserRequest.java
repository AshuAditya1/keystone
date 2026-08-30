package com.meridian.keystone.dto;

import com.meridian.keystone.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        @Size(max = 255)
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
        String password,

        @NotBlank(message = "Full name is required")
        @Size(max = 150)
        String fullName,

        @NotNull(message = "Role is required")
        Role role,

        /** Required when role is CUSTOMER — that is what scopes their data. */
        Long customerId) {
}
