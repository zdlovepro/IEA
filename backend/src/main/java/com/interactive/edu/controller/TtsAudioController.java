package com.interactive.edu.controller;

import com.interactive.edu.config.StorageProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/tts")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "local", matchIfMissing = true)
public class TtsAudioController {

    private static final String AUDIO_PATH_PREFIX = "/api/v1/tts/audio/";

    private final StorageProperties storageProperties;

    @GetMapping("/audio/**")
    public ResponseEntity<Resource> getAudio(HttpServletRequest request) throws Exception {
        String requestUri = request.getRequestURI();
        int prefixIndex = requestUri.indexOf(AUDIO_PATH_PREFIX);
        if (prefixIndex < 0) {
            throw new NoSuchElementException("TTS audio not found");
        }

        String encodedObjectKey = requestUri.substring(prefixIndex + AUDIO_PATH_PREFIX.length());
        String objectKey = URLDecoder.decode(encodedObjectKey, StandardCharsets.UTF_8);

        Path baseDir = Path.of(storageProperties.getLocalBaseDir()).toAbsolutePath().normalize();
        Path file = baseDir.resolve(objectKey).normalize();
        if (!file.startsWith(baseDir) || !Files.exists(file) || !Files.isRegularFile(file)) {
            throw new NoSuchElementException("TTS audio not found");
        }

        MediaType mediaType = resolveMediaType(file);
        Resource resource = new FileSystemResource(file);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .body(resource);
    }

    private MediaType resolveMediaType(Path file) {
        String filename = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".wav")) {
            return MediaType.parseMediaType("audio/wav");
        }
        if (filename.endsWith(".mp3")) {
            return MediaType.parseMediaType("audio/mpeg");
        }
        if (filename.endsWith(".pcm")) {
            return MediaType.parseMediaType("audio/L16");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
