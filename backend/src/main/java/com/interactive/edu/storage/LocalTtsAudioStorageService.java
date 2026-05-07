package com.interactive.edu.storage;

import com.interactive.edu.client.TtsClient;
import com.interactive.edu.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "local", matchIfMissing = true)
@ConditionalOnBean(TtsClient.class)
@RequiredArgsConstructor
public class LocalTtsAudioStorageService implements TtsAudioStorageService {

    private final StorageProperties storageProperties;

    @Override
    public String uploadAndSign(String objectKey, byte[] audioData, String format, Integer expiryMins) {
        try {
            Path baseDir = Path.of(storageProperties.getLocalBaseDir()).toAbsolutePath().normalize();
            Path target = baseDir.resolve(objectKey).normalize();
            if (!target.startsWith(baseDir)) {
                throw new IllegalArgumentException("Illegal TTS audio path.");
            }

            Files.createDirectories(target.getParent());
            Files.write(target, audioData);

            String normalizedKey = objectKey.replace('\\', '/');
            log.info("TTS audio stored locally: key={}, bytes={}", normalizedKey, audioData.length);
            return "/api/v1/tts/audio/" + normalizedKey;
        } catch (Exception ex) {
            throw new IllegalStateException("Local TTS audio storage failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public String generateObjectKey(String format) {
        LocalDate today = LocalDate.now();
        return String.format("tts-audio/%d/%02d/%s.%s",
                today.getYear(),
                today.getMonthValue(),
                UUID.randomUUID(),
                StringUtils.hasText(format) ? format : "wav");
    }
}
