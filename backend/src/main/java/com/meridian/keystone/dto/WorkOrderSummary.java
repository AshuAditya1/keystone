package com.meridian.keystone.dto;

import com.meridian.keystone.domain.Priority;
import com.meridian.keystone.domain.SlaStatus;
import com.meridian.keystone.domain.WorkOrder;
import com.meridian.keystone.domain.WorkOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Row shape for lists and the Kanban board. Requires customer/site/assignee to
 * be loaded (the repository entity graph does that in one query).
 */
public record WorkOrderSummary(
        Long id,
        String code,
        String title,
        Priority priority,
        WorkOrderStatus status,
        Long customerId,
        String customerName,
        Long siteId,
        String siteName,
        Long assigneeId,
        String assigneeName,
        Instant slaDueAt,
        SlaStatus slaStatus,
        Instant completedAt,
        int totalLaborMinutes,
        BigDecimal totalPartsCost,
        Instant createdAt,
        Instant updatedAt) {

    public static WorkOrderSummary from(WorkOrder wo) {
        return new WorkOrderSummary(
                wo.getId(),
                wo.getCode(),
                wo.getTitle(),
                wo.getPriority(),
                wo.getStatus(),
                wo.getCustomer().getId(),
                wo.getCustomer().getName(),
                wo.getSite().getId(),
                wo.getSite().getName(),
                wo.getAssignee() == null ? null : wo.getAssignee().getId(),
                wo.getAssignee() == null ? null : wo.getAssignee().getFullName(),
                wo.getSlaDueAt(),
                wo.getSlaStatus(),
                wo.getCompletedAt(),
                wo.getTotalLaborMinutes(),
                wo.getTotalPartsCost(),
                wo.getCreatedAt(),
                wo.getUpdatedAt());
    }
}
