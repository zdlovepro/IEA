package com.interactive.edu.vo.record;

import java.time.Instant;

public record InterruptRecordView(
        String interruptId,
        String sessionId,
        String coursewareId,
        Integer pageIndex,
        Double currentTime,
        String asrText,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
