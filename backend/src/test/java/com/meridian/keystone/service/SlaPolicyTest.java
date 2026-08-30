package com.meridian.keystone.service;

import com.meridian.keystone.domain.Priority;
import com.meridian.keystone.domain.SlaStatus;
import com.meridian.keystone.domain.WorkOrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The SLA rules.
 *
 * <p>The same policy is applied on the write path (a transition recalculates the
 * SLA state) and on the read path (the scheduled sweep re-derives it). Testing
 * the policy directly is what stops those two from drifting apart, and the
 * boundary cases below — completing exactly on the deadline, a cancelled job that
 * is technically overdue, a late job that must stay late — are the ones that
 * would otherwise be argued about rather than decided.
 */
class SlaPolicyTest {

    private static final Instant RAISED = Instant.parse("2026-05-01T00:00:00Z");
    private static final Instant DUE = Instant.parse("2026-05-02T00:00:00Z");   // MEDIUM: +24h
    private static final Instant AT_RISK_FROM = Instant.parse("2026-05-01T18:00:00Z"); // 75%

    // ----------------------------------------------------------------- windows

    @Test
    @DisplayName("each priority gets its own response window")
    void windowsByPriority() {
        assertThat(SlaPolicy.targetFor(Priority.URGENT)).isEqualTo(Duration.ofHours(4));
        assertThat(SlaPolicy.targetFor(Priority.HIGH)).isEqualTo(Duration.ofHours(8));
        assertThat(SlaPolicy.targetFor(Priority.MEDIUM)).isEqualTo(Duration.ofHours(24));
        assertThat(SlaPolicy.targetFor(Priority.LOW)).isEqualTo(Duration.ofHours(72));
    }

    @Test
    @DisplayName("more urgent work always gets a shorter window")
    void urgencyShortensTheWindow() {
        Priority[] ascending = {Priority.LOW, Priority.MEDIUM, Priority.HIGH, Priority.URGENT};
        for (int i = 1; i < ascending.length; i++) {
            assertThat(SlaPolicy.targetFor(ascending[i]))
                    .as("%s vs %s", ascending[i], ascending[i - 1])
                    .isLessThan(SlaPolicy.targetFor(ascending[i - 1]));
        }
    }

    @Test
    @DisplayName("the deadline is the window measured from when the job was raised")
    void deadlineIsRaisedPlusWindow() {
        assertThat(SlaPolicy.dueAt(Priority.MEDIUM, RAISED)).isEqualTo(DUE);
        assertThat(SlaPolicy.dueAt(Priority.URGENT, RAISED))
                .isEqualTo(Instant.parse("2026-05-01T04:00:00Z"));
    }

    // -------------------------------------------------------- running the clock

    @Test
    @DisplayName("a job is on track until three quarters of its window has gone")
    void onTrackForTheFirstThreeQuarters() {
        assertThat(evaluateOpen(RAISED)).isEqualTo(SlaStatus.ON_TRACK);
        assertThat(evaluateOpen(AT_RISK_FROM.minusSeconds(1))).isEqualTo(SlaStatus.ON_TRACK);
    }

    @Test
    @DisplayName("at 75% of the window it starts flagging as at risk")
    void atRiskFromThreeQuarters() {
        assertThat(evaluateOpen(AT_RISK_FROM)).isEqualTo(SlaStatus.AT_RISK);
        assertThat(evaluateOpen(DUE.minusSeconds(1))).isEqualTo(SlaStatus.AT_RISK);
    }

    @Test
    @DisplayName("the deadline itself is not yet a breach")
    void deadlineBoundaryIsNotABreach() {
        assertThat(evaluateOpen(DUE)).isEqualTo(SlaStatus.AT_RISK);
        assertThat(evaluateOpen(DUE.plusSeconds(1))).isEqualTo(SlaStatus.BREACHED);
    }

    @Test
    @DisplayName("the at-risk point is three quarters of the way through")
    void atRiskPointIsComputedFromTheWindow() {
        assertThat(SlaPolicy.atRiskFrom(RAISED, DUE)).isEqualTo(AT_RISK_FROM);
    }

    @Test
    @DisplayName("a nonsensical window degrades to the deadline rather than throwing")
    void atRiskPointIsDefensive() {
        assertThat(SlaPolicy.atRiskFrom(null, DUE)).isEqualTo(DUE);
        assertThat(SlaPolicy.atRiskFrom(DUE, DUE)).isEqualTo(DUE);
        assertThat(SlaPolicy.atRiskFrom(DUE.plusSeconds(60), DUE)).isEqualTo(DUE);
    }

    // ------------------------------------------------------- freezing the result

    @Test
    @DisplayName("finishing on time is on track, and stays on track forever after")
    void completingOnTimeFreezesTheResult() {
        Instant completed = DUE.minusSeconds(3600);
        Instant muchLater = DUE.plus(Duration.ofDays(30));
        assertThat(SlaPolicy.evaluate(RAISED, DUE, completed,
                WorkOrderStatus.COMPLETED, muchLater)).isEqualTo(SlaStatus.ON_TRACK);
    }

    @Test
    @DisplayName("finishing exactly on the deadline counts as on time")
    void completingOnTheDeadlineIsOnTime() {
        assertThat(SlaPolicy.evaluate(RAISED, DUE, DUE,
                WorkOrderStatus.COMPLETED, DUE)).isEqualTo(SlaStatus.ON_TRACK);
    }

    @Test
    @DisplayName("finishing late is a breach, and remains one")
    void completingLateIsPermanentlyABreach() {
        Instant completed = DUE.plusSeconds(1);
        assertThat(SlaPolicy.evaluate(RAISED, DUE, completed,
                WorkOrderStatus.COMPLETED, completed)).isEqualTo(SlaStatus.BREACHED);
        assertThat(SlaPolicy.evaluate(RAISED, DUE, completed,
                WorkOrderStatus.CLOSED, completed.plus(Duration.ofDays(365))))
                .isEqualTo(SlaStatus.BREACHED);
    }

    @Test
    @DisplayName("a cancelled job is not a breach, however overdue it looks")
    void cancelledWorkIsNotABreach() {
        assertThat(SlaPolicy.evaluate(RAISED, DUE, null,
                WorkOrderStatus.CANCELLED, DUE.plus(Duration.ofDays(7))))
                .isEqualTo(SlaStatus.ON_TRACK);
        assertThat(SlaPolicy.evaluate(RAISED, DUE, DUE.plusSeconds(600),
                WorkOrderStatus.CANCELLED, DUE.plus(Duration.ofDays(7))))
                .isEqualTo(SlaStatus.ON_TRACK);
    }

    @Test
    @DisplayName("no deadline means no SLA to breach")
    void noDeadlineMeansOnTrack() {
        assertThat(SlaPolicy.evaluate(RAISED, null, null,
                WorkOrderStatus.IN_PROGRESS, DUE.plus(Duration.ofDays(7))))
                .isEqualTo(SlaStatus.ON_TRACK);
    }

    private SlaStatus evaluateOpen(Instant now) {
        return SlaPolicy.evaluate(RAISED, DUE, null, WorkOrderStatus.IN_PROGRESS, now);
    }
}
