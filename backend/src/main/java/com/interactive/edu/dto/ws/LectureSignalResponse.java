package com.interactive.edu.dto.ws;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LectureSignalResponse(
        String type,
        String sessionId,
        Map<String, Object> payload
) {

    public static LectureSignalResponse ack(String sessionId, Map<String, Object> payload) {
        return new LectureSignalResponse("ack", sessionId, payload == null ? Map.of() : payload);
    }

    public static LectureSignalResponse state(String sessionId, Map<String, Object> payload) {
        return new LectureSignalResponse("state", sessionId, payload == null ? Map.of() : payload);
    }

    public static LectureSignalResponse error(String sessionId, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message);
        return new LectureSignalResponse("error", sessionId, payload);
    }
}
