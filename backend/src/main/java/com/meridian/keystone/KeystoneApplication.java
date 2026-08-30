package com.meridian.keystone;

import com.meridian.keystone.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * KEYSTONE — Field Service Management Platform.
 * Entry point for the Spring Boot back end.
 *
 * Scheduling is deliberately not enabled here. {@code SchedulingConfig} owns it,
 * so a test slice that does not import that class runs without the SLA sweep
 * firing underneath it — which only works if this class stays out of it.
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class KeystoneApplication {

    public static void main(String[] args) {
        SpringApplication.run(KeystoneApplication.class, args);
    }
}
