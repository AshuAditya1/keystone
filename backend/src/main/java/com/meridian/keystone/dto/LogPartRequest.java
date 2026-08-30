package com.meridian.keystone.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record LogPartRequest(
        @NotNull(message = "Part is required")
        Long partId,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 10000, message = "Quantity looks unreasonable")
        Integer quantity) {
}
