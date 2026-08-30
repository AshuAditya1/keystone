package com.meridian.keystone.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 150, message = "Name must be at most 150 characters")
        String name,

        @Email(message = "Must be a valid email address")
        @Size(max = 255)
        String contactEmail,

        @Size(max = 50)
        String contactPhone) {
}
