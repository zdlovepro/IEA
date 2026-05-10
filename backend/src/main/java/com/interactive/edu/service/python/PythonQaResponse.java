package com.interactive.edu.service.python;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

public record PythonQaResponse(
        String answer,
        List<EvidencePayload> evidence,
        @JsonProperty("latencyMs") long latencyMs
) {

    public List<EvidencePayload> safeEvidence() {
        return evidence == null ? Collections.emptyList() : evidence;
    }

    public record EvidencePayload(
            String source,
            String text,
            @JsonProperty("pageIndex") Integer pageIndex,
            @JsonProperty("chunkId") String chunkId
    ) {
    }
}
