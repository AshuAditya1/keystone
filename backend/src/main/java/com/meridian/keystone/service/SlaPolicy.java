package com.meridian.keystone.service;

import com.meridian.keystone.domain.Priority;
import com.meridian.keystone.domain.SlaStatus;
import com.meridian.keystone.domain.WorkOrderStatus;

import java.time.Duration;
import java.time.Instant;

/**
 * The SLA rules, in one place: how long each priority gets, and how a work
 * order's SLA state is derived from its deadline.
 *
 * <p>Pure functions with no Spring or database dependencies, so the rules can
 * be unit-tested directly and cannot drift between the write path (transitions)
 * and the read path (the scheduled sweep).
 */
public final class SlaPolicy {

    private SlaPolicy() {
    }

    /** Fraction of the window that must elapse before a job counts as at risk. */
    private static final double AT_RISK_FRACTION = 0.75;

    /** Time allowed from creation to completion, by priority. */
    public static Duration targetFor(Priority priority) {
        return switch (priority) {
            case URGENT -> Duration.ofHours(4);
            case HIGH -> Duration.ofHours(8);
            case MEDIUM -> Duration.ofHours(24);
            case LOW -> Duration.ofHours(72);
        };
    }

    /** The deadline for a job of this priority raised at {@code raisedAt}. */
    public static Instant dueAt(Priority priority, Instant raisedAt) {
        return raisedAt.plus(targetFor(priority));
    }

    /**
     * Derive the SLA state.
     *
     * <p>A finished job freezes at its actual outcome — completing late is a
     * breach forever, and completing on time cannot later decay to breached
     * just because the clock kept running. A cancelled job is not a breach.
     */
    public static SlaStatus evaluate(Instant windowStart,
                                     Instant dueAt,
                                     Instant completedAt,
                                     WorkOrderStatus status,
                                     Instant now) {
        if (dueAt == null) {
            return SlaStatus.ON_TRACK;
        }
        if (status == WorkOrderStatus.CANCELLED) {
            return SlaStatus.ON_TRACK;
        }
        if (completedAt != null) {
            return completedAt.isAfter(dueAt) ? SlaStatus.BREACHED : SlaStatus.ON_TRACK;
        }
        if (now.isAfter(dueAt)) {
            return SlaStatus.BREACHED;
        }
        return now.isBefore(atRiskFrom(windowStart, dueAt))
                ? SlaStatus.ON_TRACK
                : SlaStatus.AT_RISK;
    }

    /** The moment a still-running job starts counting as at risk. */
    public static Instant atRiskFrom(Instant windowStart, Instant dueAt) {
        if (windowStart == null || !windowStart.isBefore(dueAt)) {
            return dueAt;
        }
        long totalMillis = Duration.between(windowStart, dueAt).toMillis();
        return windowStart.plusMillis(Math.round(totalMillis * AT_RISK_FRACTION));
    }
}
