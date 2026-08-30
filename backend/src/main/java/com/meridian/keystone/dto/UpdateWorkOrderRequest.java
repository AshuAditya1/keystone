package com.meridian.keystone.dto;

import com.meridian.keystone.domain.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateWorkOrderRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200)
        String title,

        @Size(max = 4000)
        String description,

        @NotNull(message = "Priority is required")
        Priority priority,

        @NotNull(message = "Site is required")
        Long siteId) {
}
