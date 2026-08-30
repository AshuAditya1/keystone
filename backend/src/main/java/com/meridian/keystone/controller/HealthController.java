package com.meridian.keystone.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Liveness endpoint. Public (no auth) so uptime checks and the frontend
 * skeleton can confirm the API is reachable.
 */
@RestController
@RequestMapping("/api/health")
@Tag(name = "Health")
public class HealthController {

    @GetMapping
    @SecurityRequirements // no auth required for this endpoint in Swagger
    @Operation(summary = "Liveness check", description = "Returns UP when the service is running.")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "keystone",
                "time", Instant.now().toString()
        );
    }
}
