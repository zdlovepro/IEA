package com.interactive.edu.client.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interactive.edu.client.TtsClient;
import com.interactive.edu.config.TtsProperties;
import com.interactive.edu.dto.tts.TtsRequest;
import com.interactive.edu.dto.tts.TtsResult;
import com.interactive.edu.exception.ErrorCode;
import com.interactive.edu.exception.TtsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 阿里云 NLS 语音合成客户端（流式 HTTP REST 实现）。
 *
 * <h2>鉴权流程</h2>
 * <ol>
 *   <li>使用 AccessKeyId + AccessKeySecret 向 NLS Token 端点换取短效 Token（24 小时有效）。</li>
 *   <li>将 Token 与 AppKey 以 Query 参数形式附在每次 TTS 请求中。</li>
 *   <li>Token 在内存中缓存，提前 60 秒自动刷新，避免每次请求重复换取。</li>
 * </ol>
 *
 * <h2>接口约定</h2>
 * <ul>
 *   <li>Token 端点：{@code POST <endpoint>/token}</li>
 *   <li>TTS 端点：{@code POST <endpoint>/stream/v1/tts?appkey=<appKey>&token=<token>}</li>
 *   <li>成功响应：HTTP 200，Content-Type: audio/*，Body 为二进制音频数据。</li>
 *   <li>失败响应：HTTP 4xx/5xx，Content-Type: application/json，Body 含 status 与 message。</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "tts", name = "enabled", havingValue = "true")
public class AliyunTtsClient implements TtsClient {

    /** 提前刷新 Token 的秒数（防止 Token 在请求途中过期）*/
    private static final long TOKEN_REFRESH_BUFFER_SECONDS = 60L;

    private final TtsProperties.Aliyun cfg;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /** 内存缓存的 NLS Token */
    private volatile String cachedToken = null;
    /** Token 过期的 Unix 时间戳（秒） */
    private volatile long tokenExpireEpochSecond = 0L;

    public AliyunTtsClient(TtsProperties props, ObjectMapper objectMapper) {
        this.cfg = props.getAliyun();
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(cfg.getConnectTimeoutMs()))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(cfg.getReadTimeoutMs()));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    // -------------------------------------------------------------------------
    // TtsClient 接口实现
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     * <p>
     * 调用阿里云 NLS 流式合成接口，返回完整音频字节数组。
     */
    @Override
    public TtsResult synthesize(TtsRequest request) {
        validateRequest(request);

        String token = getOrRefreshToken();
        // URL 含 token，禁止打印到日志（安全规约 10 §4）
        String url = cfg.getEndpoint() + cfg.getTtsPath()
                + "?appkey=" + cfg.getAppKey()
                + "&token=" + token;

        Map<String, Object> body = buildTtsBody(request);
        log.debug("TTS synthesize, text.length={}, voice={}, endpoint={}",
                request.getText().length(), body.get("voice"), cfg.getEndpoint());

        // TODO: 接入重试机制（如 Resilience4j Retry），网络抖动时最多重试 2 次
        long start = System.currentTimeMillis();
        try {
            ResponseEntity<byte[]> response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL)
                    .body(body)
                    .retrieve()
                    .toEntity(byte[].class);

            byte[] audioData = response.getBody();
            String requestId = response.getHeaders().getFirst("X-NLS-RequestId");

            log.info("TTS synthesize ok, text.length={}, audio.bytes={}, requestId={}, cost={}ms",
                    request.getText().length(),
                    audioData != null ? audioData.length : 0,
                    requestId,
                    System.currentTimeMillis() - start);

            String format = request.getFormat() != null ? request.getFormat() : cfg.getFormat();
            int sampleRate = request.getSampleRate() != null ? request.getSampleRate() : cfg.getSampleRate();

            return TtsResult.builder()
                    .audioData(audioData)
                    .format(format)
                    .sampleRate(sampleRate)
                    .requestId(requestId)
                    .build();

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("TTS synthesize failed, status={}, body={}, cost={}ms",
                    e.getStatusCode(), e.getResponseBodyAsString(), System.currentTimeMillis() - start);
            throw new TtsException(ErrorCode.TTS_SYNTHESIS_FAILED,
                    "TTS 合成失败（HTTP " + e.getStatusCode() + "）：" + e.getResponseBodyAsString(), e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * 使用 Spring {@code @Async} 在 {@code ttsTaskExecutor} 线程池中执行合成任务。
     * 异常不会静默丢失，会通过 CompletableFuture 传播给调用方。
     */
    @Override
    @Async("ttsTaskExecutor")
    public CompletableFuture<TtsResult> synthesizeAsync(TtsRequest request) {
        try {
            TtsResult result = synthesize(request);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("TTS async synthesize failed, err={}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    // -------------------------------------------------------------------------
    // Token 管理（内存缓存 + 自动刷新）
    // -------------------------------------------------------------------------

    /**
     * 获取有效 Token。
     * <p>
     * 若缓存中的 Token 尚未到期（留有 60 秒缓冲），直接返回；否则重新向 NLS 换取。
     * 方法加 {@code synchronized} 避免并发情况下重复刷新 Token。
     */
    private synchronized String getOrRefreshToken() {
        long now = Instant.now().getEpochSecond();
        if (cachedToken != null && now < tokenExpireEpochSecond - TOKEN_REFRESH_BUFFER_SECONDS) {
            return cachedToken;
        }

        log.info("Fetching new Aliyun NLS token...");
        String tokenUrl = cfg.getEndpoint() + cfg.getTokenPath();

        // 鉴权信息不写入日志，仅在调试级别使用
        Map<String, String> tokenBody = new HashMap<>();
        tokenBody.put("AccessKeyId", cfg.getAccessKeyId());
        tokenBody.put("AccessKeySecret", cfg.getAccessKeySecret());

        try {
            String responseStr = restClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(tokenBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseStr);
            JsonNode tokenNode = root.path("Token");

            if (tokenNode.isMissingNode() || tokenNode.path("Id").asText().isEmpty()) {
                throw new TtsException(ErrorCode.TTS_TOKEN_FETCH_FAILED, "Token 响应结构异常（响应已脱敏，请检查 AppKey 及网络）");
            }

            cachedToken = tokenNode.path("Id").asText();
            tokenExpireEpochSecond = tokenNode.path("ExpireTime").asLong();

            log.info("Aliyun NLS token refreshed, expiresAt={}", tokenExpireEpochSecond);
            return cachedToken;

        } catch (TtsException e) {
            throw e;
        } catch (Exception e) {
            throw new TtsException(ErrorCode.TTS_TOKEN_FETCH_FAILED, "TTS Token 获取失败：" + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // 内部工具方法
    // -------------------------------------------------------------------------

    private void validateRequest(TtsRequest request) {
        if (request == null || request.getText() == null || request.getText().isBlank()) {
            throw new TtsException(ErrorCode.TTS_INVALID_REQUEST, "TTS 合成文本不能为空");
        }
        if (request.getText().length() > 1000) {
            throw new TtsException(ErrorCode.TTS_INVALID_REQUEST,
                    "TTS 合成文本不能超过 1000 个字符，当前长度：" + request.getText().length());
        }
    }

    private Map<String, Object> buildTtsBody(TtsRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("text", request.getText());
        body.put("voice", request.getVoice() != null ? request.getVoice() : cfg.getVoice());
        body.put("format", request.getFormat() != null ? request.getFormat() : cfg.getFormat());
        body.put("sample_rate", request.getSampleRate() != null ? request.getSampleRate() : cfg.getSampleRate());
        body.put("speech_rate", request.getSpeechRate() != null ? request.getSpeechRate() : cfg.getSpeechRate());
        body.put("pitch_rate", request.getPitchRate() != null ? request.getPitchRate() : cfg.getPitchRate());
        body.put("volume", request.getVolume() != null ? request.getVolume() : cfg.getVolume());
        return body;
    }
}
