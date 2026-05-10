package com.interactive.edu.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interactive.edu.service.lecture.LectureService;
import com.interactive.edu.service.record.LectureRecordService;
import com.interactive.edu.vo.courseware.CurrentNodeView;
import com.interactive.edu.vo.record.InterruptRecordView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LectureWebSocketHandlerTest {

    @Mock
    private LectureService lectureService;

    @Mock
    private LectureRecordService lectureRecordService;

    @Mock
    private WebSocketSession webSocketSession;

    private LectureWebSocketHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new LectureWebSocketHandler(lectureService, lectureRecordService, objectMapper);

        when(webSocketSession.getId()).thenReturn("ws-1");
        when(webSocketSession.getUri()).thenReturn(URI.create("ws://localhost/ws/lecture?sessionId=sess_ws_1"));
        when(webSocketSession.getAttributes()).thenReturn(new HashMap<>());
        when(webSocketSession.isOpen()).thenReturn(true);
    }

    @Test
    @DisplayName("interrupt creates record and stores asr text")
    void interrupt_createsRecordAndAcknowledges() throws Exception {
        handler.afterConnectionEstablished(webSocketSession);

        LectureService.LectureRealtimeState interruptedState = new LectureService.LectureRealtimeState(
                "sess_ws_1",
                "cware_ws_1",
                "INTERRUPTED",
                3,
                12.3,
                Instant.now(),
                new CurrentNodeView("node_3", 3, "Page 3 content", null)
        );
        InterruptRecordView createdRecord = new InterruptRecordView(
                "intr_1",
                "sess_ws_1",
                "cware_ws_1",
                3,
                12.3,
                null,
                "INTERRUPTED",
                Instant.now(),
                Instant.now()
        );
        InterruptRecordView updatedRecord = new InterruptRecordView(
                "intr_1",
                "sess_ws_1",
                "cware_ws_1",
                3,
                12.3,
                "老师这里没听清",
                "INTERRUPTED",
                Instant.now(),
                Instant.now()
        );
        when(lectureService.interrupt("sess_ws_1", 3, 12.3)).thenReturn(interruptedState);
        when(lectureRecordService.createInterruptRecord("sess_ws_1", "cware_ws_1", 3, 12.3)).thenReturn(createdRecord);
        when(lectureRecordService.updateLatestInterruptAsrText("sess_ws_1", "老师这里没听清")).thenReturn(updatedRecord);

        handler.handleMessage(webSocketSession, new TextMessage("""
                {"type":"interrupt","sessionId":"sess_ws_1","pageIndex":3,"currentTime":12.3,"payload":{"asrText":"老师这里没听清"}}
                """));

        verify(lectureService).interrupt("sess_ws_1", 3, 12.3);
        verify(lectureRecordService).createInterruptRecord("sess_ws_1", "cware_ws_1", 3, 12.3);
        verify(lectureRecordService).updateLatestInterruptAsrText("sess_ws_1", "老师这里没听清");

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(messageCaptor.capture());
        JsonNode payload = objectMapper.readTree(messageCaptor.getValue().getPayload());

        assertThat(payload.path("type").asText()).isEqualTo("ack");
        assertThat(payload.path("payload").path("interruptId").asText()).isEqualTo("intr_1");
        assertThat(payload.path("payload").path("asrText").asText()).isEqualTo("老师这里没听清");
    }

    @Test
    @DisplayName("resume updates latest interrupt record and returns ack plus state")
    void resume_marksRecordResumedAndSendsState() throws Exception {
        handler.afterConnectionEstablished(webSocketSession);

        when(lectureService.resumeFromBreakpoint("sess_ws_1")).thenReturn(new LectureService.ResumeState(
                "sess_ws_1",
                2,
                18.6,
                new CurrentNodeView("node_2", 2, "Page 2 content", null)
        ));

        handler.handleMessage(webSocketSession, new TextMessage("""
                {"type":"resume","sessionId":"sess_ws_1"}
                """));

        verify(lectureRecordService).tryMarkLatestInterruptResumed("sess_ws_1");

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession, times(2)).sendMessage(messageCaptor.capture());
        List<TextMessage> messages = messageCaptor.getAllValues();
        JsonNode ackPayload = objectMapper.readTree(messages.get(0).getPayload());
        JsonNode statePayload = objectMapper.readTree(messages.get(1).getPayload());

        assertThat(ackPayload.path("type").asText()).isEqualTo("ack");
        assertThat(ackPayload.path("payload").path("status").asText()).isEqualTo("RESUMING");
        assertThat(statePayload.path("type").asText()).isEqualTo("state");
        assertThat(statePayload.path("payload").path("status").asText()).isEqualTo("PLAYING");
        assertThat(statePayload.path("payload").path("currentTime").asDouble()).isEqualTo(18.6);
    }
}
