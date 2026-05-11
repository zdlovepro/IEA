package com.interactive.edu.client.impl;

import com.interactive.edu.client.AsrClient;
import com.interactive.edu.dto.asr.AsrRequest;
import com.interactive.edu.dto.asr.AsrResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LocalMockAsrClient implements AsrClient {

    @Override
    public AsrResult recognize(AsrRequest request) {
        log.info(
                "Using local mock ASR. filename={}, contentType={}, size={}, sessionId={}, pageIndex={}",
                request.getFilename(),
                request.getContentType(),
                request.getAudioSize(),
                request.getSessionId(),
                request.getPageIndex()
        );
        return AsrResult.builder()
                .text("这是一个模拟语音问题")
                .confidence(0.8d)
                .durationMs(1L)
                .provider("local-mock")
                .model("mock-asr")
                .build();
    }
}
