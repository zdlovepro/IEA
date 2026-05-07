package com.interactive.edu.client.impl;

import com.interactive.edu.client.TtsClient;
import com.interactive.edu.config.TtsProperties;
import com.interactive.edu.dto.tts.TtsRequest;
import com.interactive.edu.dto.tts.TtsResult;
import com.interactive.edu.exception.ErrorCode;
import com.interactive.edu.exception.TtsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 阿里云 DashScope TTS 客户端。
 * <p>
 * 保留 Aliyun 命名以兼容现有 provider 与配置结构，
 * 底层实现已切换为 DashScope Java SDK（CosyVoice）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "tts", name = "enabled", havingValue = "true")
public class AliyunTtsClient implements TtsClient {

    private static final int MAX_DASHSCOPE_TEXT_LENGTH = 20_000;
    private static final Set<String> SUPPORTED_FORMATS = Set.of("wav", "mp3", "pcm");
    private static final Set<Integer> SUPPORTED_SAMPLE_RATES =
            Set.of(8000, 16000, 22050, 24000, 44100, 48000);

    private final TtsProperties.Aliyun cfg;
    private final DashScopeSpeechSynthesizerFactory speechSynthesizerFactory;

    public AliyunTtsClient(TtsProperties props, DashScopeSpeechSynthesizerFactory speechSynthesizerFactory) {
        this.cfg = props.getAliyun();
        this.speechSynthesizerFactory = speechSynthesizerFactory;
    }

    @Override
    public TtsResult synthesize(TtsRequest request) {
        validateRequest(request);
        DashScopeTtsOptions options = buildOptions(request);

        log.debug("TTS synthesize, text.length={}, model={}, voice={}, format={}, sampleRate={}",
                request.getText().length(),
                options.model(),
                options.voice(),
                options.format(),
                options.sampleRate());

        long start = System.currentTimeMillis();
        DashScopeSpeechSynthesizer synthesizer = null;
        try {
            synthesizer = speechSynthesizerFactory.create(options);
            byte[] audioData = toByteArray(synthesizer.call(request.getText()));
            String requestId = synthesizer.getLastRequestId();

            log.info("TTS synthesize ok, text.length={}, audio.bytes={}, requestId={}, cost={}ms",
                    request.getText().length(),
                    audioData.length,
                    requestId,
                    System.currentTimeMillis() - start);

            return TtsResult.builder()
                    .audioData(audioData)
                    .format(options.format())
                    .sampleRate(options.sampleRate())
                    .requestId(requestId)
                    .build();
        } catch (TtsException e) {
            throw e;
        } catch (Exception e) {
            log.error("TTS synthesize failed, model={}, voice={}, reason={}, cost={}ms",
                    options.model(),
                    options.voice(),
                    e.getMessage(),
                    System.currentTimeMillis() - start);
            throw new TtsException(ErrorCode.TTS_SYNTHESIS_FAILED, "DashScope TTS 合成失败：" + e.getMessage(), e);
        } finally {
            closeQuietly(synthesizer);
        }
    }

    @Override
    @Async("ttsTaskExecutor")
    public CompletableFuture<TtsResult> synthesizeAsync(TtsRequest request) {
        try {
            return CompletableFuture.completedFuture(synthesize(request));
        } catch (Exception e) {
            log.error("TTS async synthesize failed, err={}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private DashScopeTtsOptions buildOptions(TtsRequest request) {
        if (!StringUtils.hasText(cfg.getApiKey())) {
            throw new TtsException(ErrorCode.TTS_INVALID_REQUEST, "DashScope API Key 缺失");
        }

        String format = normalizeFormat(request.getFormat() != null ? request.getFormat() : cfg.getFormat());
        int sampleRate = request.getSampleRate() != null ? request.getSampleRate() : cfg.getSampleRate();
        if (!SUPPORTED_FORMATS.contains(format)) {
            throw new TtsException(ErrorCode.TTS_INVALID_REQUEST, "不支持的音频格式：" + format);
        }
        if (!SUPPORTED_SAMPLE_RATES.contains(sampleRate)) {
            throw new TtsException(ErrorCode.TTS_INVALID_REQUEST, "不支持的采样率：" + sampleRate);
        }

        String model = StringUtils.hasText(cfg.getModel()) ? cfg.getModel() : "cosyvoice-v3-flash";
        String voice = StringUtils.hasText(request.getVoice()) ? request.getVoice() : cfg.getVoice();
        if (!StringUtils.hasText(voice)) {
            throw new TtsException(ErrorCode.TTS_INVALID_REQUEST, "TTS 音色不能为空");
        }

        int volume = clamp(request.getVolume() != null ? request.getVolume() : cfg.getVolume(), 0, 100);
        float speechRate = mapDashScopeRate(request.getSpeechRate() != null ? request.getSpeechRate() : cfg.getSpeechRate());
        float pitchRate = mapDashScopeRate(request.getPitchRate() != null ? request.getPitchRate() : cfg.getPitchRate());

        return new DashScopeTtsOptions(
                cfg.getApiKey(),
                model,
                voice,
                StringUtils.hasText(cfg.getWebsocketUrl())
                        ? cfg.getWebsocketUrl()
                        : "wss://dashscope.aliyuncs.com/api-ws/v1/inference",
                format,
                sampleRate,
                speechRate,
                pitchRate,
                volume);
    }

    private void validateRequest(TtsRequest request) {
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            throw new TtsException(ErrorCode.TTS_INVALID_REQUEST, "TTS 合成文本不能为空");
        }
        if (request.getText().length() > MAX_DASHSCOPE_TEXT_LENGTH) {
            throw new TtsException(
                    ErrorCode.TTS_INVALID_REQUEST,
                    "DashScope TTS 单次文本长度不能超过 " + MAX_DASHSCOPE_TEXT_LENGTH + " 字符");
        }
    }

    private byte[] toByteArray(ByteBuffer audio) {
        if (audio == null) {
            return new byte[0];
        }
        ByteBuffer copy = audio.asReadOnlyBuffer();
        copy.rewind();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }

    private float mapDashScopeRate(int legacyRate) {
        if (legacyRate >= 0) {
            return clamp(1.0f + (legacyRate / 500.0f), 0.5f, 2.0f);
        }
        return clamp(1.0f + (legacyRate / 1000.0f), 0.5f, 2.0f);
    }

    private String normalizeFormat(String format) {
        return format == null ? "mp3" : format.trim().toLowerCase(Locale.ROOT);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void closeQuietly(DashScopeSpeechSynthesizer synthesizer) {
        if (synthesizer == null) {
            return;
        }
        try {
            synthesizer.close();
        } catch (Exception ex) {
            log.debug("Ignore DashScope synthesizer close failure: {}", ex.getMessage());
        }
    }
}
