package com.meridian.keystone.dto;

import com.meridian.keystone.domain.Part;

import java.math.BigDecimal;

public record PartView(
        Long id,
        String sku,
        String name,
        BigDecimal unitCost,
        int stockQuantity) {

    public static PartView from(Part part) {
        return new PartView(
                part.getId(),
                part.getSku(),
                part.getName(),
                part.getUnitCost(),
                part.getStockQuantity());
    }
}
