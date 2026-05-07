package com.interactive.edu.storage;

import com.interactive.edu.client.TtsClient;
import com.interactive.edu.config.MinioProperties;
import com.interactive.edu.config.TtsProperties;
import com.interactive.edu.exception.ErrorCode;
import com.interactive.edu.exception.TtsException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 基于 MinIO 的 TTS 音频存储服务。
 * <p>
 * 将 TTS 合成产生的音频字节流上传至 MinIO，并通过 {@code GetPresignedObjectUrl}
 * 生成带有效期的临时直链，供客户端直接播放，无需经过后端中转流量。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "minio")
@ConditionalOnBean(TtsClient.class)
@RequiredArgsConstructor
public class MinioTtsAudioStorageService implements TtsAudioStorageService {

    private static final Map<String, String> FORMAT_CONTENT_TYPE = Map.of(
            "wav", "audio/wav",
            "mp3", "audio/mpeg",
            "pcm", "audio/L16"
    );
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final TtsProperties ttsProperties;

    /**
     * {@inheritDoc}
     * <p>
     * 步骤：
     * <ol>
     *   <li>将 audioData 通过 ByteArrayInputStream 写入 MinIO。</li>
     *   <li>调用 {@code GetPresignedObjectUrl} 生成 HTTP GET 预签名直链。</li>
     * </ol>
     */
    @Override
    public String uploadAndSign(String objectKey, byte[] audioData, String format, Integer expiryMins) {
        if (audioData == null || audioData.length == 0) {
            throw new TtsException(ErrorCode.TTS_AUDIO_UPLOAD_FAILED, "音频数据为空，无法上传");
        }

        String bucket = minioProperties.getBucket();
        String contentType = FORMAT_CONTENT_TYPE.getOrDefault(
                format != null ? format.toLowerCase() : "", DEFAULT_CONTENT_TYPE);
        int expiry = expiryMins != null ? expiryMins : ttsProperties.getPresignedExpiryMinutes();

        log.debug("TTS audio upload: bucket={}, key={}, bytes={}, format={}, expiryMins={}",
                bucket, objectKey, audioData.length, format, expiry);

        try {
            // 1. 上传音频数据
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(new ByteArrayInputStream(audioData), audioData.length, -1)
                            .contentType(contentType)
                            .build()
            );

            // 2. 生成预签名 GET 直链
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(expiry, TimeUnit.MINUTES)
                            .build()
            );

            log.info("TTS audio uploaded and signed: key={}, bytes={}, expiryMins={}", objectKey, audioData.length, expiry);
            return url;

        } catch (Exception e) {
            log.error("TTS audio upload/sign failed: key={}, error={}", objectKey, e.getMessage(), e);
            throw new TtsException(ErrorCode.TTS_AUDIO_UPLOAD_FAILED,
                    "TTS 音频上传或预签名失败：" + e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * 生成格式：{@code tts-audio/{yyyy}/{MM}/{uuid}.{format}}
     */
    @Override
    public String generateObjectKey(String format) {
        LocalDate today = LocalDate.now();
        return String.format("tts-audio/%d/%02d/%s.%s",
                today.getYear(), today.getMonthValue(),
                UUID.randomUUID(), format != null ? format : "wav");
    }
}
