package com.meridian.keystone.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Credentials submitted to {@code POST /api/auth/login}.
 */
public record LoginRequest(
        @NotBlank(message = "email is required")
        @Email(message = "must be a valid email address")
        String email,

        @NotBlank(message = "password is required")
        String password) {
}
