package com.meridian.keystone.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The governed work-order lifecycle. Only the transitions declared here are
 * legal; the service layer enforces them and rejects anything else (HTTP 409).
 *
 * <pre>
 * NEW ─▶ ASSIGNED ─▶ IN_PROGRESS ─▶ COMPLETED ─▶ CLOSED (terminal)
 *  │        │  ▲          │  ▲                │
 *  │        │  └─ (unassign)  └─▶ ON_HOLD ─▶ IN_PROGRESS
 *  │        │                                  (reopen from COMPLETED)
 *  └────────┴──────────────────────▶ CANCELLED (terminal)
 * </pre>
 */
public enum WorkOrderStatus {
    NEW,
    ASSIGNED,
    IN_PROGRESS,
    ON_HOLD,
    COMPLETED,
    CLOSED,
    CANCELLED;

    private static final Map<WorkOrderStatus, Set<WorkOrderStatus>> ALLOWED;

    static {
        Map<WorkOrderStatus, Set<WorkOrderStatus>> m = new EnumMap<>(WorkOrderStatus.class);
        m.put(NEW, EnumSet.of(ASSIGNED, CANCELLED));
        m.put(ASSIGNED, EnumSet.of(IN_PROGRESS, NEW, CANCELLED));
        m.put(IN_PROGRESS, EnumSet.of(ON_HOLD, COMPLETED, CANCELLED));
        m.put(ON_HOLD, EnumSet.of(IN_PROGRESS, CANCELLED));
        m.put(COMPLETED, EnumSet.of(CLOSED, IN_PROGRESS));
        m.put(CLOSED, EnumSet.noneOf(WorkOrderStatus.class));
        m.put(CANCELLED, EnumSet.noneOf(WorkOrderStatus.class));
        ALLOWED = Collections.unmodifiableMap(m);
    }

    /** Terminal states cannot transition any further. */
    public boolean isTerminal() {
        return this == CLOSED || this == CANCELLED;
    }

    /** True when this order can still be edited (not terminal). */
    public boolean isOpen() {
        return !isTerminal();
    }

    /** Whether a direct transition from this status to {@code target} is legal. */
    public boolean canTransitionTo(WorkOrderStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /** The set of statuses this status may move to. */
    public Set<WorkOrderStatus> allowedTargets() {
        return ALLOWED.getOrDefault(this, Set.of());
    }
}
