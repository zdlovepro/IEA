package com.interactive.edu.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @Value("${spring.application.name:interactive-edu-backend}")
    private String appName;

    @Value("${spring.profiles.active:default}")
    private String profile;

    @GetMapping
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "app", appName,
                "profile", profile,
                "timestamp", Instant.now().toString()
        );
    }
}