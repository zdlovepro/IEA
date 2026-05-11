package com.interactive.edu.dto.asr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsrResult {

    private String text;
    private double confidence;
    private long durationMs;
    private String provider;
    private String model;
    private String requestId;
    private String language;
    private String emotion;
}
