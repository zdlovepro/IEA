package com.interactive.edu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "asr")
public class AsrProperties {

    private boolean enabled = false;
    private String provider = "local";
    private Qwen qwen = new Qwen();

    @Data
    public static class Qwen {
        private String apiKey;
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        private String model = "qwen3-asr-flash";
        private boolean enableItn = true;
        private String language;
        private int maxFileSizeMb = 10;
        private int timeoutSeconds = 60;
    }
}
