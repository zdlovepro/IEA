package com.interactive.edu.client;

import com.interactive.edu.client.impl.AliyunTtsClient;
import com.interactive.edu.client.impl.DashScopeSpeechSynthesizer;
import com.interactive.edu.client.impl.DashScopeSpeechSynthesizerFactory;
import com.interactive.edu.client.impl.DashScopeTtsOptions;
import com.interactive.edu.config.TtsProperties;
import com.interactive.edu.dto.tts.TtsRequest;
import com.interactive.edu.dto.tts.TtsResult;
import com.interactive.edu.enums.TtsProvider;
import com.interactive.edu.exception.ErrorCode;
import com.interactive.edu.exception.TtsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AliyunTtsClientTest {

    private static final byte[] FAKE_AUDIO = new byte[]{0x52, 0x49, 0x46, 0x46};

    @Mock
    private DashScopeSpeechSynthesizerFactory speechSynthesizerFactory;

    @Mock
    private DashScopeSpeechSynthesizer speechSynthesizer;

    private AliyunTtsClient client;

    @BeforeEach
    void setUp() {
        client = new AliyunTtsClient(buildProps(), speechSynthesizerFactory);
    }

    @Test
    @DisplayName("text 为 null 时抛出 TTS_INVALID_REQUEST")
    void synthesize_nullText_throwsInvalidRequest() {
        assertTtsException(TtsRequest.builder().build(), ErrorCode.TTS_INVALID_REQUEST);
    }

    @Test
    @DisplayName("text 为空字符串时抛出 TTS_INVALID_REQUEST")
    void synthesize_emptyText_throwsInvalidRequest() {
        assertTtsException(TtsRequest.builder().text("").build(), ErrorCode.TTS_INVALID_REQUEST);
    }

    @Test
    @DisplayName("text 为空白字符串时抛出 TTS_INVALID_REQUEST")
    void synthesize_blankText_throwsInvalidRequest() {
        assertTtsException(TtsRequest.builder().text("   ").build(), ErrorCode.TTS_INVALID_REQUEST);
    }

    @Test
    @DisplayName("text 超过 20000 字符时抛出 TTS_INVALID_REQUEST")
    void synthesize_textTooLong_throwsInvalidRequest() {
        assertTtsException(TtsRequest.builder().text("a".repeat(20_001)).build(), ErrorCode.TTS_INVALID_REQUEST);
    }

    @Test
    @DisplayName("text 恰好 20000 字符时允许合成")
    void synthesize_textExactly20000_passesValidation() throws Exception {
        when(speechSynthesizerFactory.create(any(DashScopeTtsOptions.class))).thenReturn(speechSynthesizer);
        when(speechSynthesizer.call(any())).thenReturn(ByteBuffer.wrap(FAKE_AUDIO));

        TtsResult result = client.synthesize(TtsRequest.builder().text("a".repeat(20_000)).build());

        assertThat(result.getAudioData()).isEqualTo(FAKE_AUDIO);
        verify(speechSynthesizer).close();
    }

    @Test
    @DisplayName("正常合成时返回音频数据和元信息")
    void synthesize_success_returnsAudioData() throws Exception {
        when(speechSynthesizerFactory.create(any(DashScopeTtsOptions.class))).thenReturn(speechSynthesizer);
        when(speechSynthesizer.call("你好")).thenReturn(ByteBuffer.wrap(FAKE_AUDIO));
        when(speechSynthesizer.getLastRequestId()).thenReturn("dashscope-request-id-123");

        TtsResult result = client.synthesize(TtsRequest.builder().text("你好").build());

        assertThat(result.getAudioData()).isEqualTo(FAKE_AUDIO);
        assertThat(result.getFormat()).isEqualTo("mp3");
        assertThat(result.getSampleRate()).isEqualTo(24000);
        assertThat(result.getRequestId()).isEqualTo("dashscope-request-id-123");
        verify(speechSynthesizer).close();
    }

    @Test
    @DisplayName("请求字段会完整映射到 DashScope 参数")
    void synthesize_customRequestFields_sentToDashScopeOptions() throws Exception {
        when(speechSynthesizerFactory.create(any(DashScopeTtsOptions.class))).thenReturn(speechSynthesizer);
        when(speechSynthesizer.call("你好")).thenReturn(ByteBuffer.wrap(FAKE_AUDIO));

        client.synthesize(TtsRequest.builder()
                .text("你好")
                .voice("longxiaochun_v2")
                .format("wav")
                .sampleRate(16000)
                .speechRate(250)
                .pitchRate(-200)
                .volume(66)
                .build());

        ArgumentCaptor<DashScopeTtsOptions> captor = ArgumentCaptor.forClass(DashScopeTtsOptions.class);
        verify(speechSynthesizerFactory).create(captor.capture());
        DashScopeTtsOptions options = captor.getValue();
        assertThat(options.apiKey()).isEqualTo("fake-dashscope-key");
        assertThat(options.model()).isEqualTo("cosyvoice-v3-flash");
        assertThat(options.voice()).isEqualTo("longxiaochun_v2");
        assertThat(options.format()).isEqualTo("wav");
        assertThat(options.sampleRate()).isEqualTo(16000);
        assertThat(options.speechRate()).isEqualTo(1.5f);
        assertThat(options.pitchRate()).isEqualTo(0.8f);
        assertThat(options.volume()).isEqualTo(66);
        assertThat(options.websocketUrl()).isEqualTo("wss://dashscope.aliyuncs.com/api-ws/v1/inference");
    }

    @Test
    @DisplayName("同步合成异常时包装为 TTS_SYNTHESIS_FAILED")
    void synthesize_sdkThrows_wrapsAsSynthesisFailed() throws Exception {
        when(speechSynthesizerFactory.create(any(DashScopeTtsOptions.class))).thenReturn(speechSynthesizer);
        when(speechSynthesizer.call("测试")).thenThrow(new IllegalStateException("sdk failed"));

        assertTtsException(TtsRequest.builder().text("测试").build(), ErrorCode.TTS_SYNTHESIS_FAILED);
        verify(speechSynthesizer).close();
    }

    @Test
    @DisplayName("异步合成成功时通过 CompletableFuture 返回结果")
    void synthesizeAsync_success_returnsFuture() throws Exception {
        when(speechSynthesizerFactory.create(any(DashScopeTtsOptions.class))).thenReturn(speechSynthesizer);
        when(speechSynthesizer.call("异步测试")).thenReturn(ByteBuffer.wrap(FAKE_AUDIO));

        CompletableFuture<TtsResult> future =
                client.synthesizeAsync(TtsRequest.builder().text("异步测试").build());

        assertThat(future.get().getAudioData()).isEqualTo(FAKE_AUDIO);
    }

    @Test
    @DisplayName("异步合成失败时 CompletableFuture 会传播 TtsException")
    void synthesizeAsync_failure_futureContainsTtsException() throws Exception {
        when(speechSynthesizerFactory.create(any(DashScopeTtsOptions.class))).thenReturn(speechSynthesizer);
        when(speechSynthesizer.call("失败测试")).thenThrow(new IllegalStateException("sdk failed"));

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
                        .isEqualTo(ErrorCode.TTS_SYNTHESIS_FAILED));
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
        aliyun.setApiKey("fake-dashscope-key");
        aliyun.setModel("cosyvoice-v3-flash");
        aliyun.setVoice("longanyang");
        aliyun.setFormat("mp3");
        aliyun.setSampleRate(24000);
        aliyun.setSpeechRate(0);
        aliyun.setPitchRate(0);
        aliyun.setVolume(50);
        aliyun.setWebsocketUrl("wss://dashscope.aliyuncs.com/api-ws/v1/inference");
        props.setAliyun(aliyun);
        return props;
    }
}
