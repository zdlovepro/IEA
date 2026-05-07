package com.interactive.edu.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interactive.edu.client.impl.AliyunTtsClient;
import com.interactive.edu.config.TtsProperties;
import com.interactive.edu.dto.tts.TtsRequest;
import com.interactive.edu.dto.tts.TtsResult;
import com.interactive.edu.enums.TtsProvider;
import com.interactive.edu.exception.ErrorCode;
import com.interactive.edu.exception.TtsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * AliyunTtsClient 单元测试。
 * <p>
 * 使用 MockRestServiceServer 拦截 HTTP 调用，无需真实阿里云凭证。
 */
class AliyunTtsClientTest {

    private static final String FAKE_ENDPOINT = "http://fake-nls.test";
    private static final String FAKE_TOKEN_JSON =
            "{\"Token\":{\"Id\":\"fake-token-id\",\"ExpireTime\":9999999999}}";
    private static final byte[] FAKE_AUDIO = new byte[]{0x52, 0x49, 0x46, 0x46}; // RIFF header

    private MockRestServiceServer mockServer;
    private AliyunTtsClient client;

    @BeforeEach
    void setUp() throws Exception {
        TtsProperties props = buildProps();

        // 构造 client 后，用反射替换其内部 RestClient 为可被 Mock 的实例
        client = new AliyunTtsClient(props, new ObjectMapper());

        // 创建 RestTemplate 级别的 mock server 并注入
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient mockedRestClient = builder.build();

        Field restClientField = AliyunTtsClient.class.getDeclaredField("restClient");
        restClientField.setAccessible(true);
        restClientField.set(client, mockedRestClient);
    }

    // -------------------------------------------------------------------------
    // 参数校验测试
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("text 为 null 时抛 TTS_INVALID_REQUEST")
    void synthesize_nullText_throwsInvalidRequest() {
        TtsRequest req = TtsRequest.builder().build(); // text = null
        assertTtsException(req, ErrorCode.TTS_INVALID_REQUEST);
    }

    @Test
    @DisplayName("text 为空字符串时抛 TTS_INVALID_REQUEST")
    void synthesize_emptyText_throwsInvalidRequest() {
        TtsRequest req = TtsRequest.builder().text("").build();
        assertTtsException(req, ErrorCode.TTS_INVALID_REQUEST);
    }

    @Test
    @DisplayName("text 为空白字符串时抛 TTS_INVALID_REQUEST")
    void synthesize_blankText_throwsInvalidRequest() {
        TtsRequest req = TtsRequest.builder().text("   ").build();
        assertTtsException(req, ErrorCode.TTS_INVALID_REQUEST);
    }

    @Test
    @DisplayName("text 超过 1000 字符时抛 TTS_INVALID_REQUEST")
    void synthesize_textTooLong_throwsInvalidRequest() {
        TtsRequest req = TtsRequest.builder().text("a".repeat(1001)).build();
        assertTtsException(req, ErrorCode.TTS_INVALID_REQUEST);
    }

    @Test
    @DisplayName("text 恰好 1000 字符时不抛异常（需走 HTTP 流程）")
    void synthesize_textExactly1000_passesValidation() {
        // 仅验证不抛 TTS_INVALID_REQUEST，后续 HTTP 请求由 Mock 控制
        mockTokenOk();
        mockSynthesisOk();

        TtsResult result = client.synthesize(TtsRequest.builder().text("a".repeat(1000)).build());
        assertThat(result.getAudioData()).isEqualTo(FAKE_AUDIO);
        mockServer.verify();
    }

    // -------------------------------------------------------------------------
    // 正常合成流程
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("正常合成：返回音频数据和元信息")
    void synthesize_success_returnsAudioData() {
        mockTokenOk();
        mockSynthesisOk();

        TtsResult result = client.synthesize(TtsRequest.builder().text("你好").build());

        assertThat(result.getAudioData()).isEqualTo(FAKE_AUDIO);
        assertThat(result.getFormat()).isEqualTo("wav");
        assertThat(result.getSampleRate()).isEqualTo(16000);
        mockServer.verify();
    }

    @Test
    @DisplayName("Token 未过期时不重复请求 Token 端点")
    void synthesize_tokenCached_notRefreshed() {
        // 预注册：只有 1 次 Token 请求 + 2 次合成请求
        mockTokenOk();
        mockSynthesisOk();
        mockSynthesisOk();

        client.synthesize(TtsRequest.builder().text("第一次").build());
        client.synthesize(TtsRequest.builder().text("第二次").build());

        // verify() 会断言所有预期都被消耗（Token 只被请求一次）
        mockServer.verify();
    }

    @Test
    @DisplayName("异步合成：CompletableFuture 正常返回结果")
    void synthesizeAsync_success_returnsFuture() throws Exception {
        mockTokenOk();
        mockSynthesisOk();

        CompletableFuture<TtsResult> future =
                client.synthesizeAsync(TtsRequest.builder().text("异步测试").build());
        TtsResult result = future.get();

        assertThat(result.getAudioData()).isEqualTo(FAKE_AUDIO);
        mockServer.verify();
    }

    // -------------------------------------------------------------------------
    // 错误场景
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Token 端点返回 401 时抛 TTS_TOKEN_FETCH_FAILED")
    void synthesize_tokenEndpoint401_throwsTokenFetchFailed() {
        mockServer.expect(requestTo(FAKE_ENDPOINT + "/token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertTtsException(TtsRequest.builder().text("测试").build(),
                ErrorCode.TTS_TOKEN_FETCH_FAILED);
    }

    @Test
    @DisplayName("Token 响应结构异常时抛 TTS_TOKEN_FETCH_FAILED")
    void synthesize_tokenResponseMalformed_throwsTokenFetchFailed() {
        mockServer.expect(requestTo(FAKE_ENDPOINT + "/token"))
                .andRespond(withSuccess("{\"error\":\"invalid\"}", MediaType.APPLICATION_JSON));

        assertTtsException(TtsRequest.builder().text("测试").build(),
                ErrorCode.TTS_TOKEN_FETCH_FAILED);
    }

    @Test
    @DisplayName("合成端点返回 500 时抛 TTS_SYNTHESIS_FAILED")
    void synthesize_synthesisEndpoint500_throwsSynthesisFailed() {
        mockTokenOk();
        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("/stream/v1/tts")))
                .andRespond(withServerError());

        assertTtsException(TtsRequest.builder().text("测试").build(),
                ErrorCode.TTS_SYNTHESIS_FAILED);
    }

    @Test
    @DisplayName("异步合成失败时 CompletableFuture 携带 TtsException")
    void synthesizeAsync_failure_futureContainsTtsException() {
        mockServer.expect(requestTo(FAKE_ENDPOINT + "/token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        CompletableFuture<TtsResult> future =
                client.synthesizeAsync(TtsRequest.builder().text("失败测试").build());

        assertThatThrownBy(() -> {
            try {
                future.get();
            } catch (ExecutionException e) {
                throw e.getCause();
            }
        }).isInstanceOf(TtsException.class)
          .satisfies(ex -> assertThat(((TtsException) ex).getErrorCode())
                  .isEqualTo(ErrorCode.TTS_TOKEN_FETCH_FAILED));
    }

    // -------------------------------------------------------------------------
    // 工具方法
    // -------------------------------------------------------------------------

    private void mockTokenOk() {
        mockServer.expect(requestTo(FAKE_ENDPOINT + "/token"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(FAKE_TOKEN_JSON, MediaType.APPLICATION_JSON));
    }

    private void mockSynthesisOk() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-NLS-RequestId", "test-request-id-123");
        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("/stream/v1/tts")))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(FAKE_AUDIO, MediaType.APPLICATION_OCTET_STREAM)
                        .headers(headers));
    }

    private void assertTtsException(TtsRequest req, ErrorCode expectedCode) {
        assertThatThrownBy(() -> client.synthesize(req))
                .isInstanceOf(TtsException.class)
                .satisfies(ex -> assertThat(((TtsException) ex).getErrorCode())
                        .isEqualTo(expectedCode));
    }

    private static TtsProperties buildProps() {
        TtsProperties props = new TtsProperties();
        props.setProvider(TtsProvider.ALIYUN);
        TtsProperties.Aliyun aliyun = new TtsProperties.Aliyun();
        aliyun.setAppKey("fake-app-key");
        aliyun.setAccessKeyId("fake-access-key-id");
        aliyun.setAccessKeySecret("fake-secret");
        aliyun.setEndpoint(FAKE_ENDPOINT);
        aliyun.setVoice("aixia");
        aliyun.setSampleRate(16000);
        aliyun.setFormat("wav");
        aliyun.setSpeechRate(0);
        aliyun.setPitchRate(0);
        aliyun.setVolume(50);
        aliyun.setConnectTimeoutMs(5000);
        aliyun.setReadTimeoutMs(30000);
        aliyun.setTokenPath("/token");
        aliyun.setTtsPath("/stream/v1/tts");
        props.setAliyun(aliyun);
        return props;
    }
}
