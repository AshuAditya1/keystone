package com.meridian.keystone.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's scheduler, which drives the periodic SLA sweep
 * ({@code SlaSweepJob}). Kept as its own class so scheduling can be disabled in
 * a test slice by simply not importing it.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
