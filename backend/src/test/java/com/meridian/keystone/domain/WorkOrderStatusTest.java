package com.meridian.keystone.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The structural half of the lifecycle: which moves exist at all, regardless of
 * who is asking.
 *
 * <p>Worth testing exhaustively rather than by example, because the state machine
 * is the one piece of this system that everything else defers to. If a stray
 * edge were added here — say NEW straight to COMPLETED — no other check in the
 * application would catch it.
 */
class WorkOrderStatusTest {

    @Test
    @DisplayName("the happy path runs NEW → ASSIGNED → IN_PROGRESS → COMPLETED → CLOSED")
    void happyPathIsWalkable() {
        assertThat(WorkOrderStatus.NEW.canTransitionTo(WorkOrderStatus.ASSIGNED)).isTrue();
        assertThat(WorkOrderStatus.ASSIGNED.canTransitionTo(WorkOrderStatus.IN_PROGRESS)).isTrue();
        assertThat(WorkOrderStatus.IN_PROGRESS.canTransitionTo(WorkOrderStatus.COMPLETED)).isTrue();
        assertThat(WorkOrderStatus.COMPLETED.canTransitionTo(WorkOrderStatus.CLOSED)).isTrue();
    }

    @Test
    @DisplayName("work can be paused and resumed")
    void pauseAndResume() {
        assertThat(WorkOrderStatus.IN_PROGRESS.canTransitionTo(WorkOrderStatus.ON_HOLD)).isTrue();
        assertThat(WorkOrderStatus.ON_HOLD.canTransitionTo(WorkOrderStatus.IN_PROGRESS)).isTrue();
    }

    @Test
    @DisplayName("a completed job can be reopened, but a closed one cannot")
    void reopenRules() {
        assertThat(WorkOrderStatus.COMPLETED.canTransitionTo(WorkOrderStatus.IN_PROGRESS)).isTrue();
        assertThat(WorkOrderStatus.CLOSED.allowedTargets()).isEmpty();
    }

    @Test
    @DisplayName("no shortcuts: a job cannot skip straight to done")
    void noShortcuts() {
        assertThat(WorkOrderStatus.NEW.canTransitionTo(WorkOrderStatus.IN_PROGRESS)).isFalse();
        assertThat(WorkOrderStatus.NEW.canTransitionTo(WorkOrderStatus.COMPLETED)).isFalse();
        assertThat(WorkOrderStatus.NEW.canTransitionTo(WorkOrderStatus.CLOSED)).isFalse();
        assertThat(WorkOrderStatus.ASSIGNED.canTransitionTo(WorkOrderStatus.COMPLETED)).isFalse();
        assertThat(WorkOrderStatus.ON_HOLD.canTransitionTo(WorkOrderStatus.COMPLETED)).isFalse();
    }

    @Test
    @DisplayName("only ASSIGNED can go back to the unassigned queue")
    void onlyAssignedReturnsToQueue() {
        for (WorkOrderStatus from : WorkOrderStatus.values()) {
            boolean expected = from == WorkOrderStatus.ASSIGNED;
            assertThat(from.canTransitionTo(WorkOrderStatus.NEW))
                    .as("%s → NEW", from)
                    .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("anything still open can be cancelled; anything terminal cannot")
    void cancellationRules() {
        Set<WorkOrderStatus> cancellable = EnumSet.of(
                WorkOrderStatus.NEW, WorkOrderStatus.ASSIGNED,
                WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.ON_HOLD);
        for (WorkOrderStatus from : WorkOrderStatus.values()) {
            assertThat(from.canTransitionTo(WorkOrderStatus.CANCELLED))
                    .as("%s → CANCELLED", from)
                    .isEqualTo(cancellable.contains(from));
        }
        // COMPLETED is deliberately not cancellable: reopen it first, so the
        // reversal is visible in the audit trail rather than implied.
        assertThat(WorkOrderStatus.COMPLETED.canTransitionTo(WorkOrderStatus.CANCELLED)).isFalse();
    }

    @Test
    @DisplayName("CLOSED and CANCELLED are the only terminal states")
    void terminalStates() {
        for (WorkOrderStatus status : WorkOrderStatus.values()) {
            boolean terminal = status == WorkOrderStatus.CLOSED
                    || status == WorkOrderStatus.CANCELLED;
            assertThat(status.isTerminal()).as("%s terminal", status).isEqualTo(terminal);
            assertThat(status.isOpen()).as("%s open", status).isEqualTo(!terminal);
        }
    }

    @ParameterizedTest
    @EnumSource(WorkOrderStatus.class)
    @DisplayName("a status is never a legal target of itself")
    void noSelfTransitions(WorkOrderStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(WorkOrderStatus.class)
    @DisplayName("allowedTargets agrees with canTransitionTo for every pair")
    void targetsAgreeWithPredicate(WorkOrderStatus from) {
        for (WorkOrderStatus to : WorkOrderStatus.values()) {
            assertThat(from.allowedTargets().contains(to))
                    .as("%s → %s", from, to)
                    .isEqualTo(from.canTransitionTo(to));
        }
    }

    @ParameterizedTest
    @EnumSource(WorkOrderStatus.class)
    @DisplayName("every non-terminal status has somewhere to go (no dead ends)")
    void noDeadEnds(WorkOrderStatus status) {
        if (status.isTerminal()) {
            assertThat(status.allowedTargets()).isEmpty();
        } else {
            assertThat(status.allowedTargets()).isNotEmpty();
        }
    }
}
