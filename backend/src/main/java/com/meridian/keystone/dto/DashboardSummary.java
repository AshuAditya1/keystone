package com.meridian.keystone.dto;

import com.meridian.keystone.domain.Priority;
import com.meridian.keystone.domain.WorkOrderStatus;

import java.util.List;
import java.util.Map;

/**
 * Dashboard metrics, computed over exactly the work orders the caller is
 * allowed to see — so a technician's numbers cover their own jobs and a
 * customer's cover their own sites.
 */
public record DashboardSummary(
        long total,
        long open,
        long unassigned,
        long slaBreached,
        long slaAtRisk,
        long completedLast7Days,
        Double avgCompletionHours,
        Map<WorkOrderStatus, Long> byStatus,
        Map<Priority, Long> byPriority,
        List<TechnicianLoad> technicianLoad,
        List<StatusHistoryView> recentActivity) {
}
