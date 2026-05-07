package com.interactive.edu.service.tts;

import com.interactive.edu.client.TtsClient;
import com.interactive.edu.config.TtsProperties;
import com.interactive.edu.dto.tts.TtsRequest;
import com.interactive.edu.dto.tts.TtsResult;
import com.interactive.edu.storage.TtsAudioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Main-chain TTS facade.
 * <p>
 * This service is intentionally best-effort:
 * when TTS is disabled, misconfigured, or temporarily unavailable,
 * it returns {@code null} instead of failing the script-generation flow.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TtsService {

    private static final int MAX_TTS_TEXT_LENGTH = 1000;

    private final TtsProperties ttsProperties;
    private final ObjectProvider<TtsClient> ttsClientProvider;
    private final ObjectProvider<TtsAudioStorageService> audioStorageServiceProvider;

    public String synthesizeToAudioUrl(String text) {
        String normalizedText = normalizeText(text);
        if (!StringUtils.hasText(normalizedText)) {
            return null;
        }

        if (!ttsProperties.isEnabled()) {
            return null;
        }

        if (!hasAliyunCredentials()) {
            log.warn("TTS is enabled but Aliyun credentials are incomplete. Skip audio generation.");
            return null;
        }

        TtsClient ttsClient = ttsClientProvider.getIfAvailable();
        TtsAudioStorageService storageService = audioStorageServiceProvider.getIfAvailable();
        if (ttsClient == null || storageService == null) {
            log.warn("TTS is enabled but runtime beans are unavailable. clientPresent={}, storagePresent={}",
                    ttsClient != null, storageService != null);
            return null;
        }

        try {
            TtsResult result = ttsClient.synthesize(TtsRequest.builder()
                    .text(normalizedText)
                    .build());

            if (result == null || result.getAudioData() == null || result.getAudioData().length == 0) {
                log.warn("TTS returned empty audio data. Skip audio URL generation.");
                return null;
            }

            String format = StringUtils.hasText(result.getFormat()) ? result.getFormat() : "wav";
            String objectKey = storageService.generateObjectKey(format);
            return storageService.uploadAndSign(objectKey, result.getAudioData(), format, null);
        } catch (Exception ex) {
            log.warn("TTS generation failed, degrade to text-only flow. reason={}", ex.getMessage());
            log.debug("TTS generation failure details", ex);
            return null;
        }
    }

    private boolean hasAliyunCredentials() {
        TtsProperties.Aliyun aliyun = ttsProperties.getAliyun();
        return aliyun != null
                && StringUtils.hasText(aliyun.getAppKey())
                && StringUtils.hasText(aliyun.getAccessKeyId())
                && StringUtils.hasText(aliyun.getAccessKeySecret());
    }

    private String normalizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.trim();
        if (normalized.length() <= MAX_TTS_TEXT_LENGTH) {
            return normalized;
        }
        log.info("TTS source text is too long ({} chars). Truncate to {} chars for synthesis.",
                normalized.length(), MAX_TTS_TEXT_LENGTH);
        return normalized.substring(0, MAX_TTS_TEXT_LENGTH);
    }
}
