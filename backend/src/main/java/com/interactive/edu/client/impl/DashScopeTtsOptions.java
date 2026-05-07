package com.interactive.edu.client.impl;

public record DashScopeTtsOptions(
        String apiKey,
        String model,
        String voice,
        String websocketUrl,
        String format,
        int sampleRate,
        float speechRate,
        float pitchRate,
        int volume) {
}
