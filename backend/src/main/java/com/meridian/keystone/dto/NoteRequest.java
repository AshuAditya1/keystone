package com.meridian.keystone.dto;

import jakarta.validation.constraints.Size;

/** An optional free-text note attached to an action (used by unassign). */
public record NoteRequest(
        @Size(max = 1000, message = "Note must be at most 1000 characters")
        String note) {
}
