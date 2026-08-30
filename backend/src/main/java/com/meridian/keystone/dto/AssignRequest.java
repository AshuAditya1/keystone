package com.meridian.keystone.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssignRequest(
        @NotNull(message = "Assignee is required")
        Long assigneeId,

        @Size(max = 1000)
        String note) {
}
