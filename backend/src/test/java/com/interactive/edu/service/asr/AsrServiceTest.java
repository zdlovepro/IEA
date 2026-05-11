package com.interactive.edu.service.asr;

import com.interactive.edu.client.impl.LocalMockAsrClient;
import com.interactive.edu.client.impl.QwenAsrClient;
import com.interactive.edu.config.AsrProperties;
import com.interactive.edu.dto.asr.AsrRequest;
import com.interactive.edu.dto.asr.AsrResult;
import com.interactive.edu.exception.BusinessException;
import com.interactive.edu.exception.ErrorCode;
import com.interactive.edu.service.record.LectureRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsrServiceTest {

    @Mock
    private LocalMockAsrClient localMockAsrClient;

    @Mock
    private QwenAsrClient qwenAsrClient;

    @Mock
    private LectureRecordService lectureRecordService;

    private AsrProperties properties;
    private AsrService asrService;

    @BeforeEach
    void setUp() {
        properties = new AsrProperties();
        asrService = new AsrService(properties, localMockAsrClient, qwenAsrClient, lectureRecordService);
    }

    @Test
    @DisplayName("uses local mock when asr is disabled")
    void recognize_disabled_usesLocalMock() {
        properties.setEnabled(false);
        AsrRequest request = sampleRequest("audio/webm", 128);
        when(localMockAsrClient.recognize(request))
                .thenReturn(mockResult("local-mock", "mock-asr", "这是一个模拟语音问题"));

        AsrResult result = asrService.recognize(request);

        assertThat(result.getProvider()).isEqualTo("local-mock");
        verify(localMockAsrClient).recognize(request);
        verify(qwenAsrClient, never()).recognize(request);
    }

    @Test
    @DisplayName("uses local mock when qwen api key is missing")
    void recognize_missingApiKey_fallsBackToLocalMock() {
        properties.setEnabled(true);
        properties.setProvider("qwen");
        properties.getQwen().setApiKey(null);
        AsrRequest request = sampleRequest("audio/mpeg", 256);
        when(localMockAsrClient.recognize(request))
                .thenReturn(mockResult("local-mock", "mock-asr", "这是一个模拟语音问题"));

        AsrResult result = asrService.recognize(request);

        assertThat(result.getModel()).isEqualTo("mock-asr");
        verify(localMockAsrClient).recognize(request);
        verify(qwenAsrClient, never()).recognize(request);
    }

    @Test
    @DisplayName("updates asr text record after successful recognition")
    void recognize_success_updatesRecord() {
        properties.setEnabled(true);
        properties.setProvider("qwen");
        properties.getQwen().setApiKey("demo-key");
        AsrRequest request = AsrRequest.builder()
                .audioData("wav".getBytes())
                .filename("question.wav")
                .contentType("audio/wav")
                .sessionId("sess_asr_record")
                .pageIndex(3)
                .build();
        when(qwenAsrClient.recognize(request))
                .thenReturn(mockResult("qwen", "qwen3-asr-flash", "这一页在讲递归"));

        AsrResult result = asrService.recognize(request);

        assertThat(result.getText()).isEqualTo("这一页在讲递归");
        verify(lectureRecordService).updateLatestInterruptAsrText("sess_asr_record", "这一页在讲递归");
    }

    @Test
    @DisplayName("record update failure does not break asr response")
    void recognize_recordFailure_doesNotBreakResponse() {
        properties.setEnabled(true);
        properties.setProvider("qwen");
        properties.getQwen().setApiKey("demo-key");
        AsrRequest request = AsrRequest.builder()
                .audioData("wav".getBytes())
                .filename("question.wav")
                .contentType("audio/wav")
                .sessionId("sess_asr_record")
                .pageIndex(3)
                .build();
        when(qwenAsrClient.recognize(request))
                .thenReturn(mockResult("qwen", "qwen3-asr-flash", "这是正常识别结果"));
        org.mockito.Mockito.doThrow(new RuntimeException("persist error"))
                .when(lectureRecordService)
                .updateLatestInterruptAsrText("sess_asr_record", "这是正常识别结果");

        AsrResult result = asrService.recognize(request);

        assertThat(result.getText()).isEqualTo("这是正常识别结果");
    }

    @Test
    @DisplayName("empty audio file returns param error")
    void recognize_emptyAudio_returnsParamError() {
        AsrRequest request = sampleRequest("audio/webm", 0);

        assertThatThrownBy(() -> asrService.recognize(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PARAM_ERROR);
    }

    @Test
    @DisplayName("oversized audio file returns param error")
    void recognize_tooLarge_returnsParamError() {
        properties.getQwen().setMaxFileSizeMb(10);
        AsrRequest request = sampleRequest("audio/webm", 10 * 1024 * 1024 + 1);

        assertThatThrownBy(() -> asrService.recognize(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PARAM_ERROR);
    }

    @Test
    @DisplayName("unsupported content type returns param error")
    void recognize_unsupportedContentType_returnsParamError() {
        AsrRequest request = sampleRequest("application/octet-stream", 64);

        assertThatThrownBy(() -> asrService.recognize(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PARAM_ERROR);
    }

    private AsrRequest sampleRequest(String contentType, int size) {
        byte[] audioData = new byte[size];
        return AsrRequest.builder()
                .audioData(audioData)
                .filename("question.webm")
                .contentType(contentType)
                .build();
    }

    private AsrResult mockResult(String provider, String model, String text) {
        return AsrResult.builder()
                .text(text)
                .confidence(0.8d)
                .durationMs(120L)
                .provider(provider)
                .model(model)
                .requestId("req_demo")
                .language("zh")
                .emotion("neutral")
                .build();
    }
}
