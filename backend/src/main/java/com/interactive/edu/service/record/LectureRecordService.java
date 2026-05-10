package com.interactive.edu.service.record;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interactive.edu.exception.BusinessException;
import com.interactive.edu.exception.ErrorCode;
import com.interactive.edu.vo.qa.EvidenceItemView;
import com.interactive.edu.vo.record.InterruptRecordView;
import com.interactive.edu.vo.record.QaRecordView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
public class LectureRecordService {

    private static final String INTERRUPTED = "INTERRUPTED";
    private static final String ANSWERED = "ANSWERED";
    private static final String RESUMED = "RESUMED";

    private final ObjectMapper objectMapper;
    private final Path recordBaseDir;
    private final ConcurrentMap<String, SessionRecordState> recordStore = new ConcurrentHashMap<>();

    public LectureRecordService(
            ObjectMapper objectMapper,
            @Value("${record.local-base-dir:./data/records}") String recordBaseDir
    ) {
        this(objectMapper, Path.of(recordBaseDir));
    }

    LectureRecordService(ObjectMapper objectMapper, Path recordBaseDir) {
        this.objectMapper = objectMapper;
        this.recordBaseDir = recordBaseDir;
    }

    public InterruptRecordView createInterruptRecord(
            String sessionId,
            String coursewareId,
            Integer pageIndex,
            Double currentTime
    ) {
        validateSessionId(sessionId);
        validateCoursewareId(coursewareId);

        Instant now = Instant.now();
        InterruptRecordView record = new InterruptRecordView(
                "intr_" + UUID.randomUUID().toString().replace("-", ""),
                sessionId.trim(),
                coursewareId.trim(),
                pageIndex,
                currentTime,
                null,
                INTERRUPTED,
                now,
                now
        );
        SessionRecordState state = stateFor(record.sessionId());
        synchronized (state) {
            state.interrupts.put(record.interruptId(), record);
            state.knownSession = true;
            persistSafely(record.sessionId(), "interrupt", record);
        }
        return record;
    }

    public InterruptRecordView updateLatestInterruptAsrText(String sessionId, String asrText) {
        validateSessionId(sessionId);
        if (!StringUtils.hasText(asrText)) {
            return requireLatestInterrupt(stateForExistingSession(sessionId));
        }

        SessionRecordState state = stateForExistingSession(sessionId);
        synchronized (state) {
            InterruptRecordView latest = requireLatestInterrupt(state);
            InterruptRecordView updated = new InterruptRecordView(
                    latest.interruptId(),
                    latest.sessionId(),
                    latest.coursewareId(),
                    latest.pageIndex(),
                    latest.currentTime(),
                    asrText.trim(),
                    latest.status(),
                    latest.createdAt(),
                    Instant.now()
            );
            state.interrupts.put(updated.interruptId(), updated);
            persistSafely(updated.sessionId(), "interrupt", updated);
            return updated;
        }
    }

    public InterruptRecordView markLatestInterruptAnswered(String sessionId) {
        return updateLatestInterruptStatus(sessionId, ANSWERED);
    }

    public InterruptRecordView markLatestInterruptResumed(String sessionId) {
        return updateLatestInterruptStatus(sessionId, RESUMED);
    }

    public QaRecordView createQaRecord(
            String sessionId,
            String coursewareId,
            Integer pageIndex,
            String question,
            String answer,
            List<EvidenceItemView> evidence,
            long latencyMs
    ) {
        validateSessionId(sessionId);
        validateCoursewareId(coursewareId);
        if (!StringUtils.hasText(question)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "question 不能为空");
        }
        if (!StringUtils.hasText(answer)) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED, "answer 不能为空");
        }

        QaRecordView record = new QaRecordView(
                "qa_" + UUID.randomUUID().toString().replace("-", ""),
                sessionId.trim(),
                coursewareId.trim(),
                pageIndex,
                question.trim(),
                answer.trim(),
                copyEvidence(evidence),
                Math.max(1, latencyMs),
                Instant.now()
        );
        SessionRecordState state = stateFor(record.sessionId());
        synchronized (state) {
            state.qaRecords.put(record.qaRecordId(), record);
            state.knownSession = true;
            persistSafely(record.sessionId(), "qa", record);
        }
        tryMarkLatestInterruptAnswered(record.sessionId());
        return record;
    }

    public SessionRecordsSnapshot getSessionRecords(String sessionId) {
        validateSessionId(sessionId);
        SessionRecordState state = stateFor(sessionId.trim());
        synchronized (state) {
            if (!state.knownSession) {
                loadFromJsonlIfPresent(sessionId.trim(), state);
            }
            if (!state.knownSession) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "lecture session not found");
            }
            return new SessionRecordsSnapshot(
                    new ArrayList<>(state.interrupts.values()),
                    new ArrayList<>(state.qaRecords.values())
            );
        }
    }

    public void registerSession(String sessionId) {
        validateSessionId(sessionId);
        SessionRecordState state = stateFor(sessionId.trim());
        synchronized (state) {
            state.knownSession = true;
        }
    }

    public void tryMarkLatestInterruptAnswered(String sessionId) {
        tryUpdateLatestInterruptStatus(sessionId, ANSWERED);
    }

    public void tryMarkLatestInterruptResumed(String sessionId) {
        tryUpdateLatestInterruptStatus(sessionId, RESUMED);
    }

    private InterruptRecordView updateLatestInterruptStatus(String sessionId, String status) {
        validateSessionId(sessionId);
        SessionRecordState state = stateForExistingSession(sessionId);
        synchronized (state) {
            InterruptRecordView latest = requireLatestInterrupt(state);
            InterruptRecordView updated = new InterruptRecordView(
                    latest.interruptId(),
                    latest.sessionId(),
                    latest.coursewareId(),
                    latest.pageIndex(),
                    latest.currentTime(),
                    latest.asrText(),
                    status,
                    latest.createdAt(),
                    Instant.now()
            );
            state.interrupts.put(updated.interruptId(), updated);
            persistSafely(updated.sessionId(), "interrupt", updated);
            return updated;
        }
    }

    private void tryUpdateLatestInterruptStatus(String sessionId, String status) {
        try {
            updateLatestInterruptStatus(sessionId, status);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() != ErrorCode.NOT_FOUND) {
                throw ex;
            }
        }
    }

    private SessionRecordState stateFor(String sessionId) {
        return recordStore.computeIfAbsent(sessionId, ignored -> new SessionRecordState());
    }

    private SessionRecordState stateForExistingSession(String sessionId) {
        SessionRecordState state = stateFor(sessionId.trim());
        synchronized (state) {
            if (!state.knownSession) {
                loadFromJsonlIfPresent(sessionId.trim(), state);
            }
            if (!state.knownSession) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "lecture session not found");
            }
            return state;
        }
    }

    private InterruptRecordView requireLatestInterrupt(SessionRecordState state) {
        List<InterruptRecordView> records = new ArrayList<>(state.interrupts.values());
        if (records.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "interrupt record not found");
        }
        return records.get(records.size() - 1);
    }

    private void loadFromJsonlIfPresent(String sessionId, SessionRecordState state) {
        Path recordFile = resolveRecordFile(sessionId);
        if (!Files.exists(recordFile)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(recordFile, StandardCharsets.UTF_8)) {
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                applyPersistedLine(state, objectMapper.readTree(line));
            }
            if (!state.interrupts.isEmpty() || !state.qaRecords.isEmpty()) {
                state.knownSession = true;
            }
        } catch (IOException ex) {
            log.warn("Failed to load lecture records from jsonl. sessionId={}, reason={}", sessionId, ex.getMessage());
        }
    }

    private void applyPersistedLine(SessionRecordState state, JsonNode lineNode) {
        String recordType = lineNode.path("recordType").asText("");
        JsonNode payloadNode = lineNode.path("payload");
        if (!payloadNode.isObject()) {
            return;
        }
        if ("interrupt".equals(recordType)) {
            InterruptRecordView record = objectMapper.convertValue(payloadNode, InterruptRecordView.class);
            state.interrupts.put(record.interruptId(), record);
        } else if ("qa".equals(recordType)) {
            QaRecordView record = objectMapper.convertValue(payloadNode, QaRecordView.class);
            state.qaRecords.put(record.qaRecordId(), record);
        }
    }

    private void persistSafely(String sessionId, String recordType, Object payload) {
        try {
            Files.createDirectories(recordBaseDir);
            String jsonLine = objectMapper.writeValueAsString(Map.of(
                    "recordType", recordType,
                    "payload", payload
            ));
            Files.writeString(
                    resolveRecordFile(sessionId),
                    jsonLine + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception ex) { // noqa: broad local persistence fallback
            log.warn(
                    "Failed to persist lecture record locally. sessionId={}, recordType={}, reason={}",
                    sessionId,
                    recordType,
                    ex.getMessage()
            );
        }
    }

    private Path resolveRecordFile(String sessionId) {
        return recordBaseDir.resolve(sessionId + ".jsonl");
    }

    private List<EvidenceItemView> copyEvidence(List<EvidenceItemView> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return List.of();
        }
        List<EvidenceItemView> copied = new ArrayList<>(evidence.size());
        for (EvidenceItemView item : evidence) {
            if (item == null) {
                continue;
            }
            copied.add(new EvidenceItemView(
                    item.source(),
                    item.text(),
                    item.pageIndex(),
                    item.chunkId()
            ));
        }
        return List.copyOf(copied);
    }

    private void validateSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "sessionId 不能为空");
        }
    }

    private void validateCoursewareId(String coursewareId) {
        if (!StringUtils.hasText(coursewareId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "coursewareId 不能为空");
        }
    }

    public record SessionRecordsSnapshot(
            List<InterruptRecordView> interrupts,
            List<QaRecordView> qaRecords
    ) {
    }

    private static final class SessionRecordState {
        private final Map<String, InterruptRecordView> interrupts = new LinkedHashMap<>();
        private final Map<String, QaRecordView> qaRecords = new LinkedHashMap<>();
        private boolean knownSession;
    }
}
