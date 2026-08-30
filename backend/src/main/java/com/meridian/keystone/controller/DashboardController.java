package com.meridian.keystone.controller;

import com.meridian.keystone.dto.DashboardSummary;
import com.meridian.keystone.security.KeystoneUserDetails;
import com.meridian.keystone.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The landing-page summary.
 *
 * <p>One endpoint for every role rather than four. The numbers are computed over
 * the caller's own visibility scope, so a manager sees the whole operation, a
 * technician sees their round, and a customer sees their own jobs — from the same
 * code path, which is why the three views cannot drift apart or leak into each
 * other.
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/summary")
    @Operation(summary = "Dashboard summary",
            description = "Counts by status and priority, SLA health, overdue and unassigned "
                    + "queues, technician workload (ops roles only) and a recent-activity feed.")
    public DashboardSummary summary(@AuthenticationPrincipal KeystoneUserDetails me) {
        return dashboard.summary(me);
    }
}
