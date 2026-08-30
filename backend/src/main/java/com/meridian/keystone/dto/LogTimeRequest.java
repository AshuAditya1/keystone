package com.meridian.keystone.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LogTimeRequest(
        @NotNull(message = "Minutes are required")
        @Min(value = 1, message = "Minutes must be at least 1")
        @Max(value = 1440, message = "Cannot log more than 24 hours at once")
        Integer minutes,

        @Size(max = 1000)
        String note) {
}
