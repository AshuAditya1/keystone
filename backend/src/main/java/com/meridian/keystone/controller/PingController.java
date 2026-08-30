package com.meridian.keystone.controller;

import com.meridian.keystone.security.KeystoneUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Small diagnostic endpoints that prove the security layers work end-to-end:
 * <ul>
 *   <li>{@code /api/ping/authenticated} — any logged-in user (401 otherwise).</li>
 *   <li>{@code /api/ping/manager} — MANAGER role only (403 for other roles).</li>
 * </ul>
 * These are handy for verifying JWT + RBAC in Week 1 before the domain APIs
 * exist. They can be removed once real endpoints are in place.
 */
@RestController
@RequestMapping("/api/ping")
@Tag(name = "Diagnostics")
public class PingController {

    @GetMapping("/authenticated")
    @Operation(summary = "Authenticated ping", description = "Succeeds for any authenticated user.")
    public Map<String, Object> authenticated(@AuthenticationPrincipal KeystoneUserDetails principal) {
        return Map.of(
                "message", "You are authenticated.",
                "email", principal.getUsername(),
                "role", principal.getRole().name());
    }

    @GetMapping("/manager")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Manager-only ping", description = "Succeeds only for the MANAGER role.")
    public Map<String, Object> managerOnly(@AuthenticationPrincipal KeystoneUserDetails principal) {
        return Map.of(
                "message", "You are a manager.",
                "email", principal.getUsername());
    }
}
