package com.meridian.keystone.service;

import com.meridian.keystone.domain.Role;
import com.meridian.keystone.domain.WorkOrderStatus;

import java.util.Comparator;
import java.util.List;

/**
 * Who may move a work order where.
 *
 * <p>Two independent gates guard every transition:
 * <ol>
 *   <li><b>Structural</b> — is the move legal at all? Owned by
 *       {@link WorkOrderStatus#canTransitionTo}.</li>
 *   <li><b>Authorization</b> — may <i>this</i> caller make it? Owned here.</li>
 * </ol>
 *
 * <p>Pure functions, so the rules are unit-testable and the same logic answers
 * both "may I do this?" (on write) and "what buttons do I show?" (on read).
 * The UI never decides; it only reflects what this class already permits.
 */
public final class WorkOrderLifecycle {

    private WorkOrderLifecycle() {
    }

    /**
     * Whether {@code role} may move a work order from {@code from} to {@code to}.
     *
     * <p>{@code isAssignee} is what stops one technician from working another
     * technician's job.
     *
     * <p>ASSIGNED and NEW are never reachable here: assigning and unassigning
     * change who owns the job as well as its status, so they have their own
     * endpoints rather than two code paths for the same state change.
     */
    public static boolean canMove(Role role,
                                  boolean isAssignee,
                                  WorkOrderStatus from,
                                  WorkOrderStatus to) {
        if (from == null || to == null || !from.canTransitionTo(to)) {
            return false;
        }
        return switch (to) {
            case CLOSED -> role == Role.MANAGER;
            case CANCELLED -> role == Role.MANAGER || role == Role.DISPATCHER;
            case ASSIGNED, NEW -> false;
            case IN_PROGRESS -> from == WorkOrderStatus.COMPLETED
                    ? role == Role.MANAGER
                    : isFieldWorker(role, isAssignee);
            case ON_HOLD, COMPLETED -> isFieldWorker(role, isAssignee);
        };
    }

    /** The manager, or the technician the job actually belongs to. */
    private static boolean isFieldWorker(Role role, boolean isAssignee) {
        return role == Role.MANAGER || (role == Role.TECHNICIAN && isAssignee);
    }

    /**
     * Every move this caller could make right now — the intersection of the
     * lifecycle's legal targets and their permissions. Sent to the client so it
     * can render exactly the available actions.
     */
    public static List<WorkOrderStatus> allowedTransitions(Role role,
                                                           boolean isAssignee,
                                                           WorkOrderStatus from) {
        if (from == null) {
            return List.of();
        }
        return from.allowedTargets().stream()
                .filter(target -> canMove(role, isAssignee, from, target))
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }

    /** Work may only be logged against a job that is actually underway. */
    public static boolean acceptsWorkLogs(WorkOrderStatus status) {
        return status == WorkOrderStatus.IN_PROGRESS || status == WorkOrderStatus.ON_HOLD;
    }
}
