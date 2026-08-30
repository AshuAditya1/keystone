package com.meridian.keystone.dto;

import com.meridian.keystone.domain.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The owning customer is always derived from the site — never taken from the
 * client — so a request cannot file work against someone else's customer.
 */
public record CreateWorkOrderRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @Size(max = 4000)
        String description,

        @NotNull(message = "Priority is required")
        Priority priority,

        @NotNull(message = "Site is required")
        Long siteId,

        /** Optional: dispatchers/managers may assign at creation time. */
        Long assigneeId) {
}
