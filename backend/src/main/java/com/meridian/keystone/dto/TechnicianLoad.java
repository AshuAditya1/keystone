package com.meridian.keystone.dto;

public record TechnicianLoad(
        Long technicianId,
        String technicianName,
        long activeCount,
        long completedCount) {
}
