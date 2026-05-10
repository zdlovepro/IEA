package com.interactive.edu.vo.record;

import com.interactive.edu.vo.qa.EvidenceItemView;

import java.time.Instant;
import java.util.List;

public record QaRecordView(
        String qaRecordId,
        String sessionId,
        String coursewareId,
        Integer pageIndex,
        String question,
        String answer,
        List<EvidenceItemView> evidence,
        long latencyMs,
        Instant createdAt
) {
}
