package com.interactive.edu.config;

import com.interactive.edu.enums.TtsProvider;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "tts")
public class TtsProperties {

    /** Whether TTS integrations should be enabled. */
    private boolean enabled = false;

    /** Current provider entrypoint. */
    private TtsProvider provider = TtsProvider.ALIYUN;

    /** Default signed URL expiry, in minutes. */
    private int presignedExpiryMinutes = 60;

    private Aliyun aliyun = new Aliyun();

    @Data
    public static class Aliyun {

        private String appKey;
        private String accessKeyId;
        private String accessKeySecret;
        private String endpoint = "https://nls-gateway.cn-shanghai.aliyuncs.com";
        private String voice = "aixia";
        private int sampleRate = 16000;
        private String format = "wav";
        private int speechRate = 0;
        private int pitchRate = 0;
        private int volume = 50;
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 30000;
        private String tokenPath = "/token";
        private String ttsPath = "/stream/v1/tts";
    }
}
