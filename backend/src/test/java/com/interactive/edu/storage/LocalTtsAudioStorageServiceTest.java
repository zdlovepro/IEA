package com.interactive.edu.storage;

import com.interactive.edu.config.StorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalTtsAudioStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("uploadAndSign 会在 local 模式写入音频文件并返回后端可访问 URL")
    void uploadAndSign_savesAudioAndReturnsBackendUrl() throws Exception {
        StorageProperties properties = new StorageProperties();
        properties.setLocalBaseDir(tempDir.toString());
        LocalTtsAudioStorageService service = new LocalTtsAudioStorageService(properties);

        byte[] audioData = new byte[]{0x52, 0x49, 0x46, 0x46};
        String objectKey = "tts-audio/2026/05/demo.wav";

        String audioUrl = service.uploadAndSign(objectKey, audioData, "wav", 30);

        assertThat(audioUrl).isEqualTo("/api/v1/tts/audio/tts-audio/2026/05/demo.wav");
        Path storedFile = tempDir.resolve(objectKey);
        assertThat(storedFile).exists().isRegularFile();
        assertThat(Files.readAllBytes(storedFile)).isEqualTo(audioData);
    }

    @Test
    @DisplayName("generateObjectKey 会保留传入格式后缀")
    void generateObjectKey_usesFormatSuffix() {
        StorageProperties properties = new StorageProperties();
        properties.setLocalBaseDir(tempDir.toString());
        LocalTtsAudioStorageService service = new LocalTtsAudioStorageService(properties);

        String key = service.generateObjectKey("mp3");

        assertThat(key).matches("tts-audio/\\d{4}/\\d{2}/[a-f0-9\\-]{36}\\.mp3");
    }
}
