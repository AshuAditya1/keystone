package com.meridian.keystone.dto;

import com.meridian.keystone.domain.WorkOrderStatus;

import java.util.List;

public record BoardColumn(
        WorkOrderStatus status,
        long count,
        List<WorkOrderSummary> items) {
}
