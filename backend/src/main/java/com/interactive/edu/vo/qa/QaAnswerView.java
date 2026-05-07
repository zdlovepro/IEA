package com.interactive.edu.vo.qa;

import java.util.List;

public record QaAnswerView(
        String answer,
        List<EvidenceItemView> evidence,
        long latencyMs
) {
}
