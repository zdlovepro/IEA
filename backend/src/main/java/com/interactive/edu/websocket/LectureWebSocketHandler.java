package com.interactive.edu.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interactive.edu.dto.ws.LectureSignalMessage;
import com.interactive.edu.dto.ws.LectureSignalResponse;
import com.interactive.edu.enums.LectureSessionStatus;
import com.interactive.edu.service.lecture.LectureService;
import com.interactive.edu.service.record.LectureRecordService;
import com.interactive.edu.vo.record.InterruptRecordView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class LectureWebSocketHandler extends TextWebSocketHandler {

    private static final String SESSION_ID_ATTRIBUTE = "lectureSessionId";

    private final LectureService lectureService;
    private final LectureRecordService lectureRecordService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = extractQuerySessionId(session);
        if (!StringUtils.hasText(sessionId)) {
            sendSafely(session, LectureSignalResponse.error(null, "sessionId query parameter is required"));
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        session.getAttributes().put(SESSION_ID_ATTRIBUTE, sessionId);
        log.info("Lecture websocket connected. wsSessionId={}, sessionId={}", session.getId(), sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String boundSessionId = getBoundSessionId(session);
        try {
            LectureSignalMessage signalMessage = objectMapper.readValue(message.getPayload(), LectureSignalMessage.class);
            String sessionId = resolveSessionId(boundSessionId, signalMessage.getSessionId());
            String messageType = normalizeType(signalMessage.getType());

            switch (messageType) {
                case "heartbeat" -> handleHeartbeat(session, sessionId);
                case "interrupt" -> handleInterrupt(session, sessionId, signalMessage);
                case "resume" -> handleResume(session, sessionId);
                case "state_sync" -> handleStateSync(session, sessionId);
                default -> sendSafely(session, LectureSignalResponse.error(sessionId, "unsupported signal type"));
            }
        } catch (JsonProcessingException ex) {
            log.warn("Invalid lecture websocket message. wsSessionId={}, reason={}", session.getId(), ex.getOriginalMessage());
            sendSafely(session, LectureSignalResponse.error(boundSessionId, "invalid websocket payload"));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("Lecture websocket request rejected. sessionId={}, reason={}", boundSessionId, ex.getMessage());
            sendSafely(session, LectureSignalResponse.error(boundSessionId, ex.getMessage()));
        } catch (Exception ex) {
            log.error("Lecture websocket handling failed. sessionId={}", boundSessionId, ex);
            sendSafely(session, LectureSignalResponse.error(boundSessionId, "internal websocket error"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info(
                "Lecture websocket closed. wsSessionId={}, sessionId={}, closeCode={}",
                session.getId(),
                getBoundSessionId(session),
                status.getCode()
        );
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn(
                "Lecture websocket transport error. wsSessionId={}, sessionId={}, reason={}",
                session.getId(),
                getBoundSessionId(session),
                exception.getMessage()
        );
        sendSafely(session, LectureSignalResponse.error(getBoundSessionId(session), "websocket transport error"));
    }

    private void handleHeartbeat(WebSocketSession session, String sessionId) {
        LectureService.LectureRealtimeState state = lectureService.updateHeartbeat(sessionId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", state.status());
        payload.put("lastSeenAt", state.lastSeenAt());
        sendSafely(session, LectureSignalResponse.ack(sessionId, payload));
    }

    private void handleInterrupt(WebSocketSession session, String sessionId, LectureSignalMessage signalMessage) {
        LectureService.LectureRealtimeState state = lectureService.interrupt(
                sessionId,
                signalMessage.getPageIndex(),
                signalMessage.getCurrentTime()
        );
        InterruptRecordView interruptRecord = createInterruptRecordSafely(state);
        String asrText = extractAsrText(signalMessage);
        if (StringUtils.hasText(asrText)) {
            interruptRecord = updateInterruptAsrTextSafely(state, asrText, interruptRecord);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", state.status());
        payload.put("pageIndex", state.currentPageIndex());
        payload.put("currentTime", state.breakpointTime());
        if (interruptRecord != null) {
            payload.put("interruptId", interruptRecord.interruptId());
        }
        if (interruptRecord != null && StringUtils.hasText(interruptRecord.asrText())) {
            payload.put("asrText", interruptRecord.asrText());
        }
        sendSafely(session, LectureSignalResponse.ack(sessionId, payload));
    }

    private void handleResume(WebSocketSession session, String sessionId) {
        LectureService.ResumeState resumeState = lectureService.resumeFromBreakpoint(sessionId);
        markLatestInterruptResumedSafely(sessionId);

        Map<String, Object> ackPayload = new LinkedHashMap<>();
        ackPayload.put("status", LectureSessionStatus.RESUMING.name());
        ackPayload.put("pageIndex", resumeState.currentPageIndex());
        ackPayload.put("breakpointTime", resumeState.breakpointTime());
        sendSafely(session, LectureSignalResponse.ack(sessionId, ackPayload));

        Map<String, Object> statePayload = new LinkedHashMap<>();
        statePayload.put("status", LectureSessionStatus.PLAYING.name());
        statePayload.put("pageIndex", resumeState.currentPageIndex());
        statePayload.put("currentTime", resumeState.breakpointTime());
        statePayload.put("currentNode", resumeState.currentNode());
        sendSafely(session, LectureSignalResponse.state(sessionId, statePayload));
    }

    private void handleStateSync(WebSocketSession session, String sessionId) {
        LectureService.LectureRealtimeState state = lectureService.getRealtimeState(sessionId);
        sendSafely(session, LectureSignalResponse.state(sessionId, payloadOf(state)));
    }

    private Map<String, Object> payloadOf(LectureService.LectureRealtimeState state) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", state.status());
        payload.put("pageIndex", state.currentPageIndex());
        payload.put("currentTime", state.breakpointTime());
        payload.put("currentNode", state.currentNode());
        payload.put("lastSeenAt", state.lastSeenAt());
        return payload;
    }

    private String extractQuerySessionId(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        return UriComponentsBuilder.fromUri(session.getUri())
                .build()
                .getQueryParams()
                .getFirst("sessionId");
    }

    private String getBoundSessionId(WebSocketSession session) {
        Object sessionId = session.getAttributes().get(SESSION_ID_ATTRIBUTE);
        return sessionId == null ? null : sessionId.toString();
    }

    private String resolveSessionId(String boundSessionId, String messageSessionId) {
        if (!StringUtils.hasText(boundSessionId) && !StringUtils.hasText(messageSessionId)) {
            throw new IllegalArgumentException("sessionId is required");
        }
        if (StringUtils.hasText(boundSessionId) && StringUtils.hasText(messageSessionId)
                && !boundSessionId.equals(messageSessionId.trim())) {
            throw new IllegalArgumentException("sessionId does not match websocket binding");
        }
        return StringUtils.hasText(messageSessionId) ? messageSessionId.trim() : boundSessionId;
    }

    private String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            throw new IllegalArgumentException("type is required");
        }
        return type.trim().toLowerCase(Locale.ROOT);
    }

    private String extractAsrText(LectureSignalMessage signalMessage) {
        if (signalMessage.getPayload() == null || signalMessage.getPayload().isEmpty()) {
            return null;
        }
        Object raw = signalMessage.getPayload().get("asrText");
        if (raw == null) {
            raw = signalMessage.getPayload().get("asr_text");
        }
        String text = raw == null ? null : raw.toString();
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private InterruptRecordView createInterruptRecordSafely(LectureService.LectureRealtimeState state) {
        try {
            return lectureRecordService.createInterruptRecord(
                    state.sessionId(),
                    state.coursewareId(),
                    state.currentPageIndex(),
                    state.breakpointTime()
            );
        } catch (Exception ex) {
            log.warn(
                    "Failed to persist interrupt record. sessionId={}, coursewareId={}, pageIndex={}, reason={}",
                    state.sessionId(),
                    state.coursewareId(),
                    state.currentPageIndex(),
                    ex.getMessage()
            );
            return null;
        }
    }

    private InterruptRecordView updateInterruptAsrTextSafely(
            LectureService.LectureRealtimeState state,
            String asrText,
            InterruptRecordView fallbackRecord
    ) {
        try {
            return lectureRecordService.updateLatestInterruptAsrText(state.sessionId(), asrText);
        } catch (Exception ex) {
            log.warn(
                    "Failed to persist interrupt ASR text. sessionId={}, coursewareId={}, pageIndex={}, reason={}",
                    state.sessionId(),
                    state.coursewareId(),
                    state.currentPageIndex(),
                    ex.getMessage()
            );
            return fallbackRecord;
        }
    }

    private void markLatestInterruptResumedSafely(String sessionId) {
        try {
            lectureRecordService.tryMarkLatestInterruptResumed(sessionId);
        } catch (Exception ex) {
            log.warn("Failed to mark interrupt resumed from websocket. sessionId={}, reason={}", sessionId, ex.getMessage());
        }
    }

    private void sendSafely(WebSocketSession session, LectureSignalResponse response) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (IOException ex) {
            log.warn(
                    "Failed to send lecture websocket message. wsSessionId={}, sessionId={}, reason={}",
                    session.getId(),
                    response.sessionId(),
                    ex.getMessage()
            );
        }
    }
}
