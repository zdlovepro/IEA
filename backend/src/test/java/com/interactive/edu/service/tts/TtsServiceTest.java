package com.interactive.edu.service.tts;

import com.interactive.edu.client.TtsClient;
import com.interactive.edu.config.TtsProperties;
import com.interactive.edu.dto.tts.TtsRequest;
import com.interactive.edu.dto.tts.TtsResult;
import com.interactive.edu.exception.ErrorCode;
import com.interactive.edu.exception.TtsException;
import com.interactive.edu.storage.TtsAudioStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TtsServiceTest {

    @Mock
    private ObjectProvider<TtsClient> ttsClientProvider;

    @Mock
    private ObjectProvider<TtsAudioStorageService> storageServiceProvider;

    @Mock
    private TtsClient ttsClient;

    @Mock
    private TtsAudioStorageService storageService;

    private TtsProperties ttsProperties;
    private TtsService service;

    @BeforeEach
    void setUp() {
        ttsProperties = buildProperties(true, true);
        service = new TtsService(ttsProperties, ttsClientProvider, storageServiceProvider);
    }

    @Test
    @DisplayName("空文本时直接降级为 null，不调用 TTS 或存储")
    void synthesizeToAudioUrl_emptyText_returnsNull() {
        assertThat(service.synthesizeToAudioUrl("   ")).isNull();

        verifyNoInteractions(ttsClient, storageService);
    }

    @Test
    @DisplayName("tts.enabled=false 时降级为 null，不影响主流程")
    void synthesizeToAudioUrl_disabled_returnsNull() {
        ttsProperties.setEnabled(false);

        assertThat(service.synthesizeToAudioUrl("讲稿内容")).isNull();

        verifyNoInteractions(ttsClient, storageService);
    }

    @Test
    @DisplayName("阿里云凭证缺失时降级为 null")
    void synthesizeToAudioUrl_missingCredentials_returnsNull() {
        ttsProperties = buildProperties(true, false);
        service = new TtsService(ttsProperties, ttsClientProvider, storageServiceProvider);

        assertThat(service.synthesizeToAudioUrl("讲稿内容")).isNull();

        verifyNoInteractions(ttsClient, storageService);
    }

    @Test
    @DisplayName("第三方 TTS 异常时降级为 null")
    void synthesizeToAudioUrl_ttsFailure_returnsNull() {
        when(ttsClientProvider.getIfAvailable()).thenReturn(ttsClient);
        when(storageServiceProvider.getIfAvailable()).thenReturn(storageService);
        when(ttsClient.synthesize(any(TtsRequest.class)))
                .thenThrow(new TtsException(ErrorCode.TTS_SYNTHESIS_FAILED, "mock 500"));

        assertThat(service.synthesizeToAudioUrl("讲稿内容")).isNull();

        verify(ttsClient).synthesize(any(TtsRequest.class));
        verifyNoInteractions(storageService);
    }

    @Test
    @DisplayName("文本超过 1000 字符时会先截断再合成")
    void synthesizeToAudioUrl_textTooLong_truncatesBeforeSynthesis() {
        String longText = "a".repeat(1200);
        byte[] audioData = new byte[]{0x01, 0x02};
        when(ttsClientProvider.getIfAvailable()).thenReturn(ttsClient);
        when(storageServiceProvider.getIfAvailable()).thenReturn(storageService);
        when(ttsClient.synthesize(any(TtsRequest.class))).thenReturn(TtsResult.builder()
                .audioData(audioData)
                .format("mp3")
                .sampleRate(24000)
                .build());
        when(storageService.generateObjectKey("mp3")).thenReturn("tts-audio/test.mp3");
        when(storageService.uploadAndSign("tts-audio/test.mp3", audioData, "mp3", null))
                .thenReturn("http://audio.test/test.mp3");

        String audioUrl = service.synthesizeToAudioUrl(longText);

        assertThat(audioUrl).isEqualTo("http://audio.test/test.mp3");

        ArgumentCaptor<TtsRequest> requestCaptor = ArgumentCaptor.forClass(TtsRequest.class);
        verify(ttsClient).synthesize(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getText()).hasSize(1000);

        verify(storageService).generateObjectKey("mp3");
        verify(storageService).uploadAndSign("tts-audio/test.mp3", audioData, "mp3", null);
    }

    @Test
    @DisplayName("TTS 返回空音频数据时降级为 null")
    void synthesizeToAudioUrl_emptyAudioData_returnsNull() {
        when(ttsClientProvider.getIfAvailable()).thenReturn(ttsClient);
        when(storageServiceProvider.getIfAvailable()).thenReturn(storageService);
        when(ttsClient.synthesize(any(TtsRequest.class))).thenReturn(TtsResult.builder()
                .audioData(new byte[0])
                .format("wav")
                .sampleRate(16000)
                .build());

        assertThat(service.synthesizeToAudioUrl("讲稿内容")).isNull();

        verify(ttsClient).synthesize(any(TtsRequest.class));
        verifyNoMoreInteractions(ttsClient);
        verifyNoInteractions(storageService);
    }

    @Test
    @DisplayName("TTS 合成成功但存储上传失败时降级为 null")
    void synthesizeToAudioUrl_uploadFailure_returnsNull() {
        byte[] audioData = new byte[]{0x01, 0x02};
        when(ttsClientProvider.getIfAvailable()).thenReturn(ttsClient);
        when(storageServiceProvider.getIfAvailable()).thenReturn(storageService);
        when(ttsClient.synthesize(any(TtsRequest.class))).thenReturn(TtsResult.builder()
                .audioData(audioData)
                .format("wav")
                .sampleRate(16000)
                .build());
        when(storageService.generateObjectKey("wav")).thenReturn("tts-audio/test.wav");
        when(storageService.uploadAndSign("tts-audio/test.wav", audioData, "wav", null))
                .thenThrow(new IllegalStateException("disk full"));

        assertThat(service.synthesizeToAudioUrl("讲稿内容")).isNull();

        verify(ttsClient).synthesize(any(TtsRequest.class));
        verify(storageService).generateObjectKey("wav");
        verify(storageService).uploadAndSign("tts-audio/test.wav", audioData, "wav", null);
    }

    private static TtsProperties buildProperties(boolean enabled, boolean withCredentials) {
        TtsProperties properties = new TtsProperties();
        properties.setEnabled(enabled);

        TtsProperties.Aliyun aliyun = new TtsProperties.Aliyun();
        aliyun.setApiKey(withCredentials ? "fake-dashscope-key" : "");
        properties.setAliyun(aliyun);
        return properties;
    }
}
