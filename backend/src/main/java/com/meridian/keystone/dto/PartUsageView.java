package com.meridian.keystone.dto;

import com.meridian.keystone.domain.PartUsage;

import java.math.BigDecimal;
import java.time.Instant;

public record PartUsageView(
        Long id,
        Long partId,
        String partSku,
        String partName,
        int quantity,
        BigDecimal unitCostAtUse,
        BigDecimal lineCost,
        Long loggedById,
        String loggedByName,
        Instant createdAt) {

    public static PartUsageView from(PartUsage usage) {
        BigDecimal line = usage.getUnitCostAtUse()
                .multiply(BigDecimal.valueOf(usage.getQuantity()));
        return new PartUsageView(
                usage.getId(),
                usage.getPart().getId(),
                usage.getPart().getSku(),
                usage.getPart().getName(),
                usage.getQuantity(),
                usage.getUnitCostAtUse(),
                line,
                usage.getLoggedBy() == null ? null : usage.getLoggedBy().getId(),
                usage.getLoggedBy() == null ? "System" : usage.getLoggedBy().getFullName(),
                usage.getCreatedAt());
    }
}
