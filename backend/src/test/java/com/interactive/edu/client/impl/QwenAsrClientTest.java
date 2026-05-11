package com.interactive.edu.client.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interactive.edu.config.AsrProperties;
import com.interactive.edu.dto.asr.AsrRequest;
import com.interactive.edu.dto.asr.AsrResult;
import com.interactive.edu.exception.ErrorCode;
import com.interactive.edu.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class QwenAsrClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("recognize webm audio with qwen asr")
    void recognize_webm_success() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockHttpResponse(
                200,
                """
                        {
                          "id": "req_webm_1",
                          "choices": [
                            {
                              "message": {
                                "content": "请问这一页在讲什么？",
                                "annotations": [
                                  {
                                    "language": "zh",
                                    "emotion": "neutral",
                                    "confidence": 0.91
                                  }
                                ]
                              }
                            }
                          ]
                        }
                        """
        );
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        QwenAsrClient client = new QwenAsrClient(newProperties("test-qwen-key"), objectMapper, httpClient);
        AsrResult result = client.recognize(AsrRequest.builder()
                .audioData("webm-audio".getBytes())
                .filename("question.webm")
                .contentType("audio/webm")
                .sessionId("sess_asr_webm")
                .pageIndex(2)
                .build());

        assertThat(result.getText()).isEqualTo("请问这一页在讲什么？");
        assertThat(result.getProvider()).isEqualTo("qwen");
        assertThat(result.getModel()).isEqualTo("qwen3-asr-flash");
        assertThat(result.getRequestId()).isEqualTo("req_webm_1");
        assertThat(result.getLanguage()).isEqualTo("zh");
        assertThat(result.getEmotion()).isEqualTo("neutral");
        assertThat(result.getConfidence()).isEqualTo(0.91d);
        assertThat(result.getDurationMs()).isGreaterThanOrEqualTo(1L);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        String requestBody = requestCaptor.getValue().bodyPublisher()
                .orElseThrow()
                .contentLength() > 0 ? readBody(requestCaptor.getValue()) : "";
        assertThat(requestBody).contains("\"model\":\"qwen3-asr-flash\"");
        assertThat(requestBody).contains("data:audio/webm;base64,");
    }

    @Test
    @DisplayName("recognize mp3 audio with qwen asr")
    void recognize_mp3_success() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockHttpResponse(
                200,
                """
                        {
                          "id": "req_mp3_1",
                          "choices": [
                            {
                              "message": {
                                "content": [
                                  { "text": "这是 mp3 识别结果" }
                                ]
                              }
                            }
                          ]
                        }
                        """
        );
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        QwenAsrClient client = new QwenAsrClient(newProperties("test-qwen-key"), objectMapper, httpClient);
        AsrResult result = client.recognize(AsrRequest.builder()
                .audioData("mp3-audio".getBytes())
                .filename("question.mp3")
                .contentType("audio/mp3")
                .build());

        assertThat(result.getText()).isEqualTo("这是 mp3 识别结果");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        String requestBody = readBody(requestCaptor.getValue());
        assertThat(requestBody).contains("data:audio/mpeg;base64,");
    }

    @Test
    @DisplayName("returns media error when qwen responds 401 without leaking api key")
    void recognize_http401_throwsServiceException(CapturedOutput output) throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockHttpResponse(401, "{\"message\":\"Unauthorized\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        QwenAsrClient client = new QwenAsrClient(newProperties("secret-test-key"), objectMapper, httpClient);

        assertThatThrownBy(() -> client.recognize(AsrRequest.builder()
                .audioData("webm-audio".getBytes())
                .filename("question.webm")
                .contentType("audio/webm")
                .build()))
                .isInstanceOf(ServiceException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.THIRD_PARTY_MEDIA_ERROR);

        assertThat(output.getOut()).doesNotContain("secret-test-key");
    }

    @Test
    @DisplayName("returns media error when qwen responds 500")
    void recognize_http500_throwsServiceException() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockHttpResponse(500, "{\"message\":\"server error\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        QwenAsrClient client = new QwenAsrClient(newProperties("test-qwen-key"), objectMapper, httpClient);

        assertThatThrownBy(() -> client.recognize(AsrRequest.builder()
                .audioData("wav-audio".getBytes())
                .filename("question.wav")
                .contentType("audio/wav")
                .build()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("语音识别服务调用失败");
    }

    @Test
    @DisplayName("returns friendly error when choices are empty")
    void recognize_choicesEmpty_throwsFriendlyException() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mockHttpResponse(200, "{\"id\":\"req_empty\",\"choices\":[]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        QwenAsrClient client = new QwenAsrClient(newProperties("test-qwen-key"), objectMapper, httpClient);

        assertThatThrownBy(() -> client.recognize(AsrRequest.builder()
                .audioData("wav-audio".getBytes())
                .filename("question.wav")
                .contentType("audio/wav")
                .build()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("语音识别结果为空");
    }

    private HttpResponse<String> mockHttpResponse(int statusCode, String body) {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }

    private AsrProperties newProperties(String apiKey) {
        AsrProperties properties = new AsrProperties();
        properties.setEnabled(true);
        properties.setProvider("qwen");
        properties.getQwen().setApiKey(apiKey);
        properties.getQwen().setModel("qwen3-asr-flash");
        properties.getQwen().setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        properties.getQwen().setTimeoutSeconds(60);
        return properties;
    }

    private String readBody(HttpRequest request) {
        TestBodySubscriber subscriber = new TestBodySubscriber();
        request.bodyPublisher().orElseThrow().subscribe(subscriber);
        return subscriber.awaitBody();
    }
}
