package com.meridian.keystone.service;

import com.meridian.keystone.domain.Priority;
import com.meridian.keystone.domain.Role;
import com.meridian.keystone.domain.SlaStatus;
import com.meridian.keystone.domain.User;
import com.meridian.keystone.domain.WorkOrder;
import com.meridian.keystone.domain.WorkOrderStatus;
import com.meridian.keystone.domain.WorkOrderStatusHistory;
import com.meridian.keystone.dto.DashboardSummary;
import com.meridian.keystone.dto.StatusHistoryView;
import com.meridian.keystone.dto.TechnicianLoad;
import com.meridian.keystone.repository.UserRepository;
import com.meridian.keystone.repository.WorkOrderRepository;
import com.meridian.keystone.repository.WorkOrderStatusHistoryRepository;
import com.meridian.keystone.security.KeystoneUserDetails;
import com.meridian.keystone.security.WorkOrderAccess;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dashboard metrics.
 *
 * <p>The numbers are computed in Java over exactly the rows
 * {@link WorkOrderAccess#scope} allows this caller to see, rather than as
 * database aggregates. That costs one extra fetch, but it means the dashboard
 * and the work-order list can never disagree about who can see what — there is
 * one scoping rule, not one per metric. At Meridian's data volume the fetch is
 * cheap; a much larger deployment would push these to grouped queries with the
 * same scope predicate applied.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int ACTIVITY_LIMIT = 15;
    private static final int RECENT_WINDOW_DAYS = 7;

    private final WorkOrderRepository workOrders;
    private final WorkOrderStatusHistoryRepository history;
    private final UserRepository users;
    private final WorkOrderAccess access;

    public DashboardService(WorkOrderRepository workOrders,
                            WorkOrderStatusHistoryRepository history,
                            UserRepository users,
                            WorkOrderAccess access) {
        this.workOrders = workOrders;
        this.history = history;
        this.users = users;
        this.access = access;
    }

    public DashboardSummary summary(KeystoneUserDetails me) {
        Instant now = Instant.now();
        Instant windowStart = now.minus(RECENT_WINDOW_DAYS, ChronoUnit.DAYS);

        List<WorkOrder> visible = workOrders.findAll(
                access.scope(me), Sort.by(Sort.Direction.DESC, "createdAt"));

        long total = visible.size();
        long open = visible.stream().filter(wo -> wo.getStatus().isOpen()).count();
        long unassigned = visible.stream()
                .filter(wo -> wo.getAssignee() == null && wo.getStatus().isOpen())
                .count();
        long breached = visible.stream()
                .filter(wo -> wo.getSlaStatus() == SlaStatus.BREACHED)
                .count();
        long atRisk = visible.stream()
                .filter(wo -> wo.getSlaStatus() == SlaStatus.AT_RISK)
                .count();
        long completedRecently = visible.stream()
                .filter(wo -> wo.getCompletedAt() != null
                        && wo.getCompletedAt().isAfter(windowStart))
                .count();

        return new DashboardSummary(
                total,
                open,
                unassigned,
                breached,
                atRisk,
                completedRecently,
                averageCompletionHours(visible),
                countByStatus(visible),
                countByPriority(visible),
                technicianLoad(visible, me),
                recentActivity(visible, me));
    }

    /** Mean hours from raised to completed, over jobs that actually finished. */
    private Double averageCompletionHours(List<WorkOrder> visible) {
        List<WorkOrder> finished = visible.stream()
                .filter(wo -> wo.getCompletedAt() != null && wo.getCreatedAt() != null)
                .toList();
        if (finished.isEmpty()) {
            return null;
        }
        double totalHours = finished.stream()
                .mapToDouble(wo -> Duration.between(wo.getCreatedAt(), wo.getCompletedAt())
                        .toMinutes() / 60.0)
                .sum();
        return Math.round((totalHours / finished.size()) * 10.0) / 10.0;
    }

    /** Every status is present, zeros included, so the UI can render stable bars. */
    private Map<WorkOrderStatus, Long> countByStatus(List<WorkOrder> visible) {
        Map<WorkOrderStatus, Long> counts = new LinkedHashMap<>();
        for (WorkOrderStatus status : WorkOrderStatus.values()) {
            counts.put(status, 0L);
        }
        for (WorkOrder wo : visible) {
            counts.merge(wo.getStatus(), 1L, Long::sum);
        }
        return counts;
    }

    private Map<Priority, Long> countByPriority(List<WorkOrder> visible) {
        Map<Priority, Long> counts = new LinkedHashMap<>();
        for (Priority priority : Priority.values()) {
            counts.put(priority, 0L);
        }
        for (WorkOrder wo : visible) {
            counts.merge(wo.getPriority(), 1L, Long::sum);
        }
        return counts;
    }

    /**
     * Who is carrying what. Only meaningful for the roles that can see the whole
     * operation, so it is empty for technicians and customers.
     */
    private List<TechnicianLoad> technicianLoad(List<WorkOrder> visible, KeystoneUserDetails me) {
        if (me.getRole() != Role.MANAGER && me.getRole() != Role.DISPATCHER) {
            return List.of();
        }
        Map<Long, long[]> tally = new HashMap<>();
        for (WorkOrder wo : visible) {
            User assignee = wo.getAssignee();
            if (assignee == null) {
                continue;
            }
            long[] counts = tally.computeIfAbsent(assignee.getId(), key -> new long[2]);
            if (wo.getStatus().isOpen() && wo.getStatus() != WorkOrderStatus.COMPLETED) {
                counts[0]++;
            }
            if (wo.getCompletedAt() != null) {
                counts[1]++;
            }
        }

        List<TechnicianLoad> load = new ArrayList<>();
        for (User technician : users.findByRoleOrderByFullNameAsc(Role.TECHNICIAN)) {
            long[] counts = tally.getOrDefault(technician.getId(), new long[2]);
            // Skip retired technicians who never carried anything.
            if (!technician.isActive() && counts[0] == 0 && counts[1] == 0) {
                continue;
            }
            load.add(new TechnicianLoad(
                    technician.getId(), technician.getFullName(), counts[0], counts[1]));
        }
        load.sort((a, b) -> Long.compare(b.activeCount(), a.activeCount()));
        return load;
    }

    /**
     * The latest lifecycle moves. Managers and dispatchers see the whole feed;
     * everyone else sees only activity on work orders already proven visible to
     * them, so the feed can never become a side channel.
     */
    private List<StatusHistoryView> recentActivity(List<WorkOrder> visible,
                                                   KeystoneUserDetails me) {
        List<WorkOrderStatusHistory> rows;
        if (me.getRole() == Role.MANAGER || me.getRole() == Role.DISPATCHER) {
            rows = history.findTop15ByOrderByCreatedAtDesc();
        } else {
            Set<Long> visibleIds = visible.stream()
                    .map(WorkOrder::getId)
                    .collect(Collectors.toSet());
            if (visibleIds.isEmpty()) {
                return List.of();
            }
            rows = history.findByWorkOrderIdInOrderByCreatedAtDesc(
                    visibleIds, PageRequest.of(0, ACTIVITY_LIMIT));
        }
        return rows.stream().map(StatusHistoryView::withWorkOrder).toList();
    }
}
