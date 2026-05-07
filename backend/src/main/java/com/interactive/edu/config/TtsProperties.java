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

        private String apiKey;
        private String model = "cosyvoice-v3-flash";
        private String websocketUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";
        private String voice = "longanyang";
        private int sampleRate = 24000;
        private String format = "mp3";
        private int speechRate = 0;
        private int pitchRate = 0;
        private int volume = 50;
    }
}
