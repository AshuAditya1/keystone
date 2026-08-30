package com.meridian.keystone.dto;

import com.meridian.keystone.domain.WorkOrderStatus;
import com.meridian.keystone.domain.WorkOrderStatusHistory;

import java.time.Instant;

public record StatusHistoryView(
        Long id,
        WorkOrderStatus fromStatus,
        WorkOrderStatus toStatus,
        Long changedById,
        String changedByName,
        String note,
        Instant createdAt,
        Long workOrderId,
        String workOrderCode) {

    /** Timeline entry within a work-order detail view. */
    public static StatusHistoryView from(WorkOrderStatusHistory h) {
        return new StatusHistoryView(
                h.getId(),
                h.getFromStatus(),
                h.getToStatus(),
                h.getChangedBy() == null ? null : h.getChangedBy().getId(),
                h.getChangedBy() == null ? "System" : h.getChangedBy().getFullName(),
                h.getNote(),
                h.getCreatedAt(),
                null,
                null);
    }

    /** Activity-feed entry, which also names the work order. */
    public static StatusHistoryView withWorkOrder(WorkOrderStatusHistory h) {
        return new StatusHistoryView(
                h.getId(),
                h.getFromStatus(),
                h.getToStatus(),
                h.getChangedBy() == null ? null : h.getChangedBy().getId(),
                h.getChangedBy() == null ? "System" : h.getChangedBy().getFullName(),
                h.getNote(),
                h.getCreatedAt(),
                h.getWorkOrder().getId(),
                h.getWorkOrder().getCode());
    }
}
