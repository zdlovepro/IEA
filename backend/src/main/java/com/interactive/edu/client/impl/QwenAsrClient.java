package com.interactive.edu.client.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interactive.edu.client.AsrClient;
import com.interactive.edu.config.AsrProperties;
import com.interactive.edu.dto.asr.AsrRequest;
import com.interactive.edu.dto.asr.AsrResult;
import com.interactive.edu.exception.ErrorCode;
import com.interactive.edu.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class QwenAsrClient implements AsrClient {

    private final AsrProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public QwenAsrClient(AsrProperties properties, ObjectMapper objectMapper) {
        this(
                properties,
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(properties.getQwen().getTimeoutSeconds()))
                        .build()
        );
    }

    QwenAsrClient(AsrProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public AsrResult recognize(AsrRequest request) {
        long startAt = System.currentTimeMillis();
        String mimeType = resolveMimeType(request.getContentType(), request.getFilename());
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(buildRequestBody(request, mimeType));
        } catch (IOException ex) {
            throw new ServiceException(ErrorCode.THIRD_PARTY_MEDIA_ERROR, "语音识别请求组装失败", ex);
        }

        HttpRequest httpRequest = HttpRequest.newBuilder(resolveEndpoint())
                .timeout(Duration.ofSeconds(properties.getQwen().getTimeoutSeconds()))
                .header("Authorization", "Bearer " + properties.getQwen().getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long durationMs = Math.max(1L, System.currentTimeMillis() - startAt);
            if (response.statusCode() >= 400) {
                log.warn(
                        "Qwen ASR request failed. status={}, filename={}, contentType={}, size={}, durationMs={}",
                        response.statusCode(),
                        request.getFilename(),
                        mimeType,
                        request.getAudioSize(),
                        durationMs
                );
                throw new ServiceException(ErrorCode.THIRD_PARTY_MEDIA_ERROR, "语音识别服务调用失败");
            }

            AsrResult result = parseResponse(response.body(), durationMs);
            log.info(
                    "Qwen ASR succeeded. requestId={}, filename={}, contentType={}, size={}, durationMs={}",
                    result.getRequestId(),
                    request.getFilename(),
                    mimeType,
                    request.getAudioSize(),
                    durationMs
            );
            return result;
        } catch (ServiceException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ServiceException(ErrorCode.THIRD_PARTY_MEDIA_ERROR, "语音识别服务调用失败", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceException(ErrorCode.THIRD_PARTY_MEDIA_ERROR, "语音识别服务调用失败", ex);
        }
    }

    private URI resolveEndpoint() {
        String baseUrl = properties.getQwen().getBaseUrl();
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalized + "/chat/completions");
    }

    private Map<String, Object> buildRequestBody(AsrRequest request, String mimeType) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getQwen().getModel());
        body.put("stream", false);

        Map<String, Object> inputAudio = new LinkedHashMap<>();
        inputAudio.put("data", buildDataUrl(request.getAudioData(), mimeType));

        Map<String, Object> contentItem = new LinkedHashMap<>();
        contentItem.put("type", "input_audio");
        contentItem.put("input_audio", inputAudio);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", List.of(contentItem));

        body.put("messages", List.of(message));
        body.put("asr_options", buildAsrOptions());
        return body;
    }

    private Map<String, Object> buildAsrOptions() {
        Map<String, Object> asrOptions = new LinkedHashMap<>();
        asrOptions.put("enable_itn", properties.getQwen().isEnableItn());
        if (StringUtils.hasText(properties.getQwen().getLanguage())) {
            asrOptions.put("language", properties.getQwen().getLanguage().trim());
        }
        return asrOptions;
    }

    private String buildDataUrl(byte[] audioData, String mimeType) {
        String base64 = Base64.getEncoder().encodeToString(audioData);
        return "data:" + mimeType + ";base64," + base64;
    }

    private AsrResult parseResponse(String responseBody, long durationMs) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choicesNode = root.path("choices");
            if (!choicesNode.isArray() || choicesNode.isEmpty()) {
                throw new ServiceException(ErrorCode.THIRD_PARTY_MEDIA_ERROR, "语音识别结果为空");
            }

            JsonNode messageNode = choicesNode.get(0).path("message");
            String text = extractMessageContent(messageNode.path("content"));
            if (!StringUtils.hasText(text)) {
                throw new ServiceException(ErrorCode.THIRD_PARTY_MEDIA_ERROR, "语音识别结果为空");
            }

            JsonNode annotationsNode = messageNode.path("annotations");
            String language = extractAnnotationValue(annotationsNode, "language");
            String emotion = extractAnnotationValue(annotationsNode, "emotion");
            double confidence = extractAnnotationConfidence(annotationsNode);

            return AsrResult.builder()
                    .text(text.trim())
                    .confidence(confidence > 0 ? confidence : 1.0d)
                    .durationMs(durationMs)
                    .provider("qwen")
                    .model(properties.getQwen().getModel())
                    .requestId(textValue(root.path("id")))
                    .language(language)
                    .emotion(emotion)
                    .build();
        } catch (ServiceException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ServiceException(ErrorCode.THIRD_PARTY_MEDIA_ERROR, "语音识别结果解析失败", ex);
        }
    }

    private String extractMessageContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return null;
        }
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode item : contentNode) {
                String value = null;
                if (item.isTextual()) {
                    value = item.asText();
                } else if (item.hasNonNull("text")) {
                    value = item.path("text").asText();
                }
                if (StringUtils.hasText(value)) {
                    parts.add(value.trim());
                }
            }
            return parts.isEmpty() ? null : String.join("", parts);
        }
        if (contentNode.isObject() && contentNode.hasNonNull("text")) {
            return contentNode.path("text").asText();
        }
        return null;
    }

    private String extractAnnotationValue(JsonNode annotationsNode, String fieldName) {
        if (annotationsNode == null || annotationsNode.isMissingNode() || annotationsNode.isNull()) {
            return null;
        }
        if (annotationsNode.isObject()) {
            return textValue(annotationsNode.path(fieldName));
        }
        if (annotationsNode.isArray()) {
            for (JsonNode annotation : annotationsNode) {
                String value = textValue(annotation.path(fieldName));
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private double extractAnnotationConfidence(JsonNode annotationsNode) {
        if (annotationsNode == null || annotationsNode.isMissingNode() || annotationsNode.isNull()) {
            return 0d;
        }
        if (annotationsNode.isObject()) {
            return numericValue(annotationsNode.path("confidence"));
        }
        if (annotationsNode.isArray()) {
            for (JsonNode annotation : annotationsNode) {
                double value = numericValue(annotation.path("confidence"));
                if (value > 0) {
                    return value;
                }
            }
        }
        return 0d;
    }

    private double numericValue(JsonNode node) {
        return node != null && node.isNumber() ? node.asDouble() : 0d;
    }

    private String textValue(JsonNode node) {
        return node != null && node.isTextual() && StringUtils.hasText(node.asText()) ? node.asText() : null;
    }

    private String resolveMimeType(String contentType, String filename) {
        if (StringUtils.hasText(contentType)) {
            String normalized = normalizeContentType(contentType);
            if (normalized != null) {
                return normalized;
            }
            throw new ServiceException(ErrorCode.THIRD_PARTY_MEDIA_ERROR, "音频格式不受支持");
        }
        String lowerFilename = filename == null ? "" : filename.toLowerCase();
        if (lowerFilename.endsWith(".webm")) {
            return "audio/webm";
        }
        if (lowerFilename.endsWith(".wav")) {
            return "audio/wav";
        }
        if (lowerFilename.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (lowerFilename.endsWith(".m4a") || lowerFilename.endsWith(".mp4")) {
            return "audio/mp4";
        }
        throw new ServiceException(ErrorCode.THIRD_PARTY_MEDIA_ERROR, "音频格式不受支持");
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return null;
        }
        String normalized = contentType.trim().toLowerCase();
        return switch (normalized) {
            case "audio/webm" -> "audio/webm";
            case "audio/wav", "audio/x-wav" -> "audio/wav";
            case "audio/mpeg", "audio/mp3" -> "audio/mpeg";
            case "audio/mp4", "audio/m4a" -> "audio/mp4";
            default -> null;
        };
    }
}
