package com.meridian.keystone.service;

import com.meridian.keystone.domain.Role;
import com.meridian.keystone.domain.WorkOrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The authorization half of the lifecycle: who may make which move.
 *
 * <p>This is the security boundary the UI is only a reflection of, so it is
 * tested as a set of properties over the whole matrix rather than by a handful of
 * examples. Several of the tests loop over every role, every from-status and
 * every to-status, which is 392 combinations — cheap here, and the only way to be
 * sure there is no accidental hole.
 */
class WorkOrderLifecycleTest {

    private static final boolean ASSIGNEE = true;
    private static final boolean NOT_ASSIGNEE = false;

    // ------------------------------------------------------------- named rules

    @Test
    @DisplayName("only a manager can close a completed job")
    void onlyManagerCloses() {
        assertCanMove(Role.MANAGER, ASSIGNEE, WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED, true);
        assertCanMove(Role.DISPATCHER, NOT_ASSIGNEE, WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED, false);
        assertCanMove(Role.TECHNICIAN, ASSIGNEE, WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED, false);
        assertCanMove(Role.CUSTOMER, NOT_ASSIGNEE, WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED, false);
    }

    @Test
    @DisplayName("the office can cancel work; the field cannot")
    void cancellationIsAnOfficeDecision() {
        assertCanMove(Role.MANAGER, NOT_ASSIGNEE, WorkOrderStatus.NEW, WorkOrderStatus.CANCELLED, true);
        assertCanMove(Role.DISPATCHER, NOT_ASSIGNEE, WorkOrderStatus.NEW, WorkOrderStatus.CANCELLED, true);
        assertCanMove(Role.TECHNICIAN, ASSIGNEE, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.CANCELLED, false);
        assertCanMove(Role.CUSTOMER, NOT_ASSIGNEE, WorkOrderStatus.NEW, WorkOrderStatus.CANCELLED, false);
    }

    @Test
    @DisplayName("a technician may start, pause, resume and complete their own job")
    void assignedTechnicianRunsTheJob() {
        assertCanMove(Role.TECHNICIAN, ASSIGNEE, WorkOrderStatus.ASSIGNED, WorkOrderStatus.IN_PROGRESS, true);
        assertCanMove(Role.TECHNICIAN, ASSIGNEE, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.ON_HOLD, true);
        assertCanMove(Role.TECHNICIAN, ASSIGNEE, WorkOrderStatus.ON_HOLD, WorkOrderStatus.IN_PROGRESS, true);
        assertCanMove(Role.TECHNICIAN, ASSIGNEE, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.COMPLETED, true);
    }

    @Test
    @DisplayName("a technician cannot touch a job that is not theirs")
    void otherTechniciansJobIsOffLimits() {
        assertCanMove(Role.TECHNICIAN, NOT_ASSIGNEE, WorkOrderStatus.ASSIGNED, WorkOrderStatus.IN_PROGRESS, false);
        assertCanMove(Role.TECHNICIAN, NOT_ASSIGNEE, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.COMPLETED, false);
        assertCanMove(Role.TECHNICIAN, NOT_ASSIGNEE, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.ON_HOLD, false);
    }

    @Test
    @DisplayName("a dispatcher does not do the fieldwork")
    void dispatcherDoesNotWorkTheJob() {
        assertCanMove(Role.DISPATCHER, NOT_ASSIGNEE, WorkOrderStatus.ASSIGNED, WorkOrderStatus.IN_PROGRESS, false);
        assertCanMove(Role.DISPATCHER, NOT_ASSIGNEE, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.COMPLETED, false);
    }

    @Test
    @DisplayName("reopening a completed job is a manager's call, even for its own technician")
    void onlyManagerReopens() {
        assertCanMove(Role.MANAGER, NOT_ASSIGNEE, WorkOrderStatus.COMPLETED, WorkOrderStatus.IN_PROGRESS, true);
        assertCanMove(Role.TECHNICIAN, ASSIGNEE, WorkOrderStatus.COMPLETED, WorkOrderStatus.IN_PROGRESS, false);
        assertCanMove(Role.DISPATCHER, NOT_ASSIGNEE, WorkOrderStatus.COMPLETED, WorkOrderStatus.IN_PROGRESS, false);
    }

    @Test
    @DisplayName("assignment is not a transition — it has its own endpoint")
    void assignmentIsNotATransition() {
        for (Role role : Role.values()) {
            for (boolean assignee : new boolean[]{ASSIGNEE, NOT_ASSIGNEE}) {
                for (WorkOrderStatus from : WorkOrderStatus.values()) {
                    assertCanMove(role, assignee, from, WorkOrderStatus.ASSIGNED, false);
                    assertCanMove(role, assignee, from, WorkOrderStatus.NEW, false);
                }
            }
        }
    }

    // -------------------------------------------------------- matrix properties

    @Test
    @DisplayName("a structurally illegal move is refused for everyone, managers included")
    void authorizationNeverOverridesTheStateMachine() {
        forEveryCombination((role, assignee, from, to) -> {
            if (!from.canTransitionTo(to)) {
                assertCanMove(role, assignee, from, to, false);
            }
        });
    }

    @Test
    @DisplayName("a manager may make any legal move except assigning or unassigning")
    void managerCanDoEverythingElse() {
        for (WorkOrderStatus from : WorkOrderStatus.values()) {
            for (WorkOrderStatus to : WorkOrderStatus.values()) {
                boolean expected = from.canTransitionTo(to)
                        && to != WorkOrderStatus.ASSIGNED
                        && to != WorkOrderStatus.NEW;
                assertCanMove(Role.MANAGER, NOT_ASSIGNEE, from, to, expected);
            }
        }
    }

    @Test
    @DisplayName("a dispatcher's only lifecycle power is cancellation")
    void dispatcherCanOnlyCancel() {
        forEveryStatusPair((from, to) -> {
            boolean expected = from.canTransitionTo(to) && to == WorkOrderStatus.CANCELLED;
            assertCanMove(Role.DISPATCHER, NOT_ASSIGNEE, from, to, expected);
        });
    }

    @Test
    @DisplayName("an unassigned technician can move nothing, anywhere")
    void unassignedTechnicianCanDoNothing() {
        forEveryStatusPair((from, to) ->
                assertCanMove(Role.TECHNICIAN, NOT_ASSIGNEE, from, to, false));
    }

    @Test
    @DisplayName("a customer can move nothing, anywhere — the portal is read-only")
    void customerCanDoNothing() {
        forEveryStatusPair((from, to) -> {
            assertCanMove(Role.CUSTOMER, NOT_ASSIGNEE, from, to, false);
            assertCanMove(Role.CUSTOMER, ASSIGNEE, from, to, false);
        });
    }

    // ------------------------------------------------------ allowedTransitions

    @Test
    @DisplayName("allowedTransitions is exactly the set canMove would accept")
    void allowedTransitionsMatchesCanMove() {
        forEveryCombination((role, assignee, from, to) -> {
            List<WorkOrderStatus> allowed =
                    WorkOrderLifecycle.allowedTransitions(role, assignee, from);
            assertThat(allowed.contains(to))
                    .as("%s (assignee=%s) %s → %s", role, assignee, from, to)
                    .isEqualTo(WorkOrderLifecycle.canMove(role, assignee, from, to));
        });
    }

    @Test
    @DisplayName("allowedTransitions is stably ordered, so the UI buttons never jump about")
    void allowedTransitionsIsSorted() {
        List<WorkOrderStatus> allowed = WorkOrderLifecycle.allowedTransitions(
                Role.MANAGER, NOT_ASSIGNEE, WorkOrderStatus.IN_PROGRESS);
        assertThat(allowed).containsExactly(
                WorkOrderStatus.CANCELLED, WorkOrderStatus.COMPLETED, WorkOrderStatus.ON_HOLD);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("terminal states offer no actions to anyone")
    void terminalStatesOfferNothing(Role role) {
        assertThat(WorkOrderLifecycle.allowedTransitions(role, ASSIGNEE, WorkOrderStatus.CLOSED))
                .isEmpty();
        assertThat(WorkOrderLifecycle.allowedTransitions(role, ASSIGNEE, WorkOrderStatus.CANCELLED))
                .isEmpty();
    }

    @Test
    @DisplayName("a customer is offered no actions in any state")
    void customerIsOfferedNothing() {
        for (WorkOrderStatus from : WorkOrderStatus.values()) {
            assertThat(WorkOrderLifecycle.allowedTransitions(Role.CUSTOMER, NOT_ASSIGNEE, from))
                    .as("customer actions in %s", from)
                    .isEmpty();
        }
    }

    // --------------------------------------------------------------- work logs

    @Test
    @DisplayName("work can only be logged against a job that is actually underway")
    void workLogsOnlyWhileUnderway() {
        for (WorkOrderStatus status : WorkOrderStatus.values()) {
            boolean expected = status == WorkOrderStatus.IN_PROGRESS
                    || status == WorkOrderStatus.ON_HOLD;
            assertThat(WorkOrderLifecycle.acceptsWorkLogs(status))
                    .as("accepts work logs in %s", status)
                    .isEqualTo(expected);
        }
    }

    // ------------------------------------------------------------ null-safety

    @Test
    @DisplayName("nulls are refused rather than thrown on")
    void nullsAreSafe() {
        assertThat(WorkOrderLifecycle.canMove(Role.MANAGER, true, null, WorkOrderStatus.CLOSED))
                .isFalse();
        assertThat(WorkOrderLifecycle.canMove(Role.MANAGER, true, WorkOrderStatus.NEW, null))
                .isFalse();
        assertThat(WorkOrderLifecycle.allowedTransitions(Role.MANAGER, true, null)).isEmpty();
    }

    // ----------------------------------------------------------------- helpers

    private void assertCanMove(Role role,
                               boolean assignee,
                               WorkOrderStatus from,
                               WorkOrderStatus to,
                               boolean expected) {
        assertThat(WorkOrderLifecycle.canMove(role, assignee, from, to))
                .as("%s (assignee=%s) %s → %s", role, assignee, from, to)
                .isEqualTo(expected);
    }

    private void forEveryStatusPair(StatusPair check) {
        for (WorkOrderStatus from : WorkOrderStatus.values()) {
            for (WorkOrderStatus to : WorkOrderStatus.values()) {
                check.accept(from, to);
            }
        }
    }

    private void forEveryCombination(Combination check) {
        for (Role role : Role.values()) {
            for (boolean assignee : new boolean[]{ASSIGNEE, NOT_ASSIGNEE}) {
                for (WorkOrderStatus from : WorkOrderStatus.values()) {
                    for (WorkOrderStatus to : WorkOrderStatus.values()) {
                        check.accept(role, assignee, from, to);
                    }
                }
            }
        }
    }

    @FunctionalInterface
    private interface StatusPair {
        void accept(WorkOrderStatus from, WorkOrderStatus to);
    }

    @FunctionalInterface
    private interface Combination {
        void accept(Role role, boolean assignee, WorkOrderStatus from, WorkOrderStatus to);
    }
}
