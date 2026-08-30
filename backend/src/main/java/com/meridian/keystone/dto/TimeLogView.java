package com.meridian.keystone.dto;

import com.meridian.keystone.domain.TimeLog;

import java.time.Instant;

public record TimeLogView(
        Long id,
        Long technicianId,
        String technicianName,
        int minutes,
        String note,
        Instant createdAt) {

    public static TimeLogView from(TimeLog log) {
        return new TimeLogView(
                log.getId(),
                log.getTechnician().getId(),
                log.getTechnician().getFullName(),
                log.getMinutes(),
                log.getNote(),
                log.getCreatedAt());
    }
}
