package com.interactive.edu.controller;

import com.interactive.edu.dto.asr.AsrResult;
import com.interactive.edu.exception.ErrorCode;
import com.interactive.edu.exception.GlobalExceptionHandler;
import com.interactive.edu.exception.ServiceException;
import com.interactive.edu.service.asr.AsrService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AsrControllerTest {

    @Mock
    private AsrService asrService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AsrController(asrService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("recognize webm returns recognized text")
    void recognize_webm_success() throws Exception {
        when(asrService.recognize(any())).thenReturn(AsrResult.builder()
                .text("这是 webm 识别结果")
                .confidence(0.93d)
                .durationMs(111L)
                .provider("qwen")
                .model("qwen3-asr-flash")
                .requestId("req_webm_1")
                .language("zh")
                .emotion("neutral")
                .build());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "question.webm",
                "audio/webm",
                "demo".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/asr/recognize")
                        .file(file)
                        .param("sessionId", "sess_1")
                        .param("pageIndex", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.text").value("这是 webm 识别结果"))
                .andExpect(jsonPath("$.data.provider").value("qwen"))
                .andExpect(jsonPath("$.data.model").value("qwen3-asr-flash"));

        ArgumentCaptor<com.interactive.edu.dto.asr.AsrRequest> captor =
                ArgumentCaptor.forClass(com.interactive.edu.dto.asr.AsrRequest.class);
        verify(asrService).recognize(captor.capture());
        assertThat(captor.getValue().getContentType()).isEqualTo("audio/webm");
        assertThat(captor.getValue().getSessionId()).isEqualTo("sess_1");
        assertThat(captor.getValue().getPageIndex()).isEqualTo(2);
    }

    @Test
    @DisplayName("recognize mp3 returns recognized text")
    void recognize_mp3_success() throws Exception {
        when(asrService.recognize(any())).thenReturn(AsrResult.builder()
                .text("这是 mp3 识别结果")
                .confidence(0.88d)
                .durationMs(98L)
                .provider("qwen")
                .model("qwen3-asr-flash")
                .requestId("req_mp3_1")
                .build());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "question.mp3",
                "audio/mp3",
                "demo".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/asr/recognize").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.text").value("这是 mp3 识别结果"));
    }

    @Test
    @DisplayName("empty file returns param error")
    void recognize_emptyFile_returnsParamError() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "question.webm",
                "audio/webm",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/v1/asr/recognize").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("qwen service errors return unified error response")
    void recognize_qwenFailure_returnsUnifiedError() throws Exception {
        when(asrService.recognize(any()))
                .thenThrow(new ServiceException(ErrorCode.THIRD_PARTY_MEDIA_ERROR, "语音识别服务调用失败"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "question.wav",
                "audio/wav",
                "demo".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/asr/recognize").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(50202))
                .andExpect(jsonPath("$.message").value("语音识别服务调用失败"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
