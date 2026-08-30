package com.meridian.keystone.service;

import com.meridian.keystone.domain.NotificationType;
import com.meridian.keystone.domain.SlaStatus;
import com.meridian.keystone.domain.WorkOrder;
import com.meridian.keystone.domain.WorkOrderStatus;
import com.meridian.keystone.repository.WorkOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import java.time.Instant;
import java.util.List;

/**
 * Periodic SLA check.
 *
 * <p>An SLA does not breach because someone opened a page — it breaches when the
 * clock passes the deadline. This job is what makes that happen on its own:
 * every minute it re-derives each unfinished job's SLA state from
 * {@link SlaPolicy} and raises an alert the first time a job goes at-risk or
 * breaches.
 *
 * <p>Because the stored {@code slaStatus} is what list filters query, refreshing
 * it here is also what keeps "show me breached jobs" agreeing with the badge the
 * user sees on each row.
 */
@Component
public class SlaSweepJob {

    private static final Logger log = LoggerFactory.getLogger(SlaSweepJob.class);

    /**
     * Finished work is excluded: a completed job's outcome was frozen at the
     * moment it completed, and closed or cancelled jobs have no deadline left.
     */
    private static final List<WorkOrderStatus> FINISHED = List.of(
            WorkOrderStatus.COMPLETED,
            WorkOrderStatus.CLOSED,
            WorkOrderStatus.CANCELLED);

    private final WorkOrderRepository workOrders;
    private final NotificationService notifications;

    public SlaSweepJob(WorkOrderRepository workOrders, NotificationService notifications) {
        this.workOrders = workOrders;
        this.notifications = notifications;
    }

    @Scheduled(
            fixedDelayString = "${app.sla.sweep-millis:60000}",
            initialDelayString = "${app.sla.initial-delay-millis:20000}")
    @SchedulerLock(name = "slaSweepLock", lockAtMostFor = "5m", lockAtLeastFor = "30s")
    @Transactional
    public void sweep() {
        Instant now = Instant.now();
        List<WorkOrder> candidates = workOrders.findByStatusNotInAndSlaDueAtIsNotNull(FINISHED);

        int changed = 0;
        int alerted = 0;
        for (WorkOrder wo : candidates) {
            SlaStatus current = wo.getSlaStatus();
            SlaStatus next = SlaPolicy.evaluate(
                    wo.getCreatedAt(),
                    wo.getSlaDueAt(),
                    wo.getCompletedAt(),
                    wo.getStatus(),
                    now);

            if (next != current) {
                wo.setSlaStatus(next);
                workOrders.save(wo);
                changed++;
            }
            // notifySlaOnce is idempotent per work order, type and recipient, so
            // running every minute cannot turn into a stream of duplicate alerts.
            if (next == SlaStatus.BREACHED) {
                notifications.notifySlaOnce(wo, NotificationType.SLA_BREACH);
                alerted++;
            } else if (next == SlaStatus.AT_RISK) {
                notifications.notifySlaOnce(wo, NotificationType.SLA_AT_RISK);
                alerted++;
            }
        }

        if (changed > 0) {
            log.info("SLA sweep: {} of {} open work orders changed state ({} alert checks)",
                    changed, candidates.size(), alerted);
        } else {
            log.debug("SLA sweep: {} open work orders, no state changes", candidates.size());
        }
    }
}
