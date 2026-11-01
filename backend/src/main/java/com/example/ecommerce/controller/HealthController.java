package com.example.ecommerce.controller;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

@RestController
public class HealthController implements HealthIndicator {

    @Override
    public Health health() {
        return Health.up().withDetail("service", "ecommerce-api").build();
    }

    @GetMapping("/api/v1/health")
    public HealthResponse apiHealth() {
        return new HealthResponse("UP", "ecommerce-api", Instant.now());
    }

    public record HealthResponse(String status, String service, Instant timestamp) {
    }
}
