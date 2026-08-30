package com.meridian.keystone.dto;

import com.meridian.keystone.domain.Priority;
import com.meridian.keystone.domain.SlaStatus;
import com.meridian.keystone.domain.WorkOrder;
import com.meridian.keystone.domain.WorkOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Full work-order view: the record itself, its audit trail, its parts and time,
 * plus what THIS caller is allowed to do next.
 *
 * {@code allowedTransitions} is the intersection of the lifecycle's legal moves
 * and the caller's permissions, computed server-side. The UI renders exactly
 * those buttons — so the screen can never offer an action the API would refuse,
 * and hiding a button is never what enforces the rule.
 */
public record WorkOrderDetail(
        Long id,
        String code,
        String title,
        String description,
        Priority priority,
        WorkOrderStatus status,
        Long customerId,
        String customerName,
        Long siteId,
        String siteName,
        String siteAddress,
        Long assigneeId,
        String assigneeName,
        Instant slaDueAt,
        SlaStatus slaStatus,
        Instant completedAt,
        int totalLaborMinutes,
        BigDecimal totalPartsCost,
        Instant createdAt,
        Instant updatedAt,
        List<StatusHistoryView> history,
        List<PartUsageView> parts,
        List<TimeLogView> timeLogs,
        List<WorkOrderStatus> allowedTransitions,
        boolean canEdit,
        boolean canAssign,
        boolean canLogWork) {

    public static WorkOrderDetail of(WorkOrder wo,
                                     List<StatusHistoryView> history,
                                     List<PartUsageView> parts,
                                     List<TimeLogView> timeLogs,
                                     List<WorkOrderStatus> allowedTransitions,
                                     boolean canEdit,
                                     boolean canAssign,
                                     boolean canLogWork) {
        return new WorkOrderDetail(
                wo.getId(),
                wo.getCode(),
                wo.getTitle(),
                wo.getDescription(),
                wo.getPriority(),
                wo.getStatus(),
                wo.getCustomer().getId(),
                wo.getCustomer().getName(),
                wo.getSite().getId(),
                wo.getSite().getName(),
                wo.getSite().getAddress(),
                wo.getAssignee() == null ? null : wo.getAssignee().getId(),
                wo.getAssignee() == null ? null : wo.getAssignee().getFullName(),
                wo.getSlaDueAt(),
                wo.getSlaStatus(),
                wo.getCompletedAt(),
                wo.getTotalLaborMinutes(),
                wo.getTotalPartsCost(),
                wo.getCreatedAt(),
                wo.getUpdatedAt(),
                history,
                parts,
                timeLogs,
                allowedTransitions,
                canEdit,
                canAssign,
                canLogWork);
    }
}
