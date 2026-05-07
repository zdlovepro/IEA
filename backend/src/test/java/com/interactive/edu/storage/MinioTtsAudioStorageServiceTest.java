package com.interactive.edu.storage;

import com.interactive.edu.config.MinioProperties;
import com.interactive.edu.config.TtsProperties;
import com.interactive.edu.exception.ErrorCode;
import com.interactive.edu.exception.TtsException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * MinioTtsAudioStorageService 单元测试。
 * <p>
 * 使用 Mockito Mock MinioClient，无需真实 MinIO 实例。
 */
@ExtendWith(MockitoExtension.class)
class MinioTtsAudioStorageServiceTest {

    private static final String BUCKET = "test-bucket";
    private static final String FAKE_URL = "http://localhost:9000/test-bucket/tts-audio/2026/04/xxx.wav?X-Amz-Expires=3600";
    private static final byte[] AUDIO_DATA = new byte[]{0x52, 0x49, 0x46, 0x46}; // RIFF

    @Mock
    private MinioClient minioClient;

    private MinioTtsAudioStorageService service;
    private TtsProperties ttsProperties;

    @BeforeEach
    void setUp() {
        MinioProperties minioProperties = new MinioProperties();
        minioProperties.setBucket(BUCKET);

        ttsProperties = new TtsProperties();
        ttsProperties.setPresignedExpiryMinutes(90);
        // 默认 presignedExpiryMinutes = 60

        service = new MinioTtsAudioStorageService(minioClient, minioProperties, ttsProperties);
    }

    // -------------------------------------------------------------------------
    // generateObjectKey
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generateObjectKey：返回符合 tts-audio/{yyyy}/{MM}/{uuid}.{format} 格式的 Key")
    void generateObjectKey_returnsCorrectPattern() {
        String key = service.generateObjectKey("wav");

        assertThat(key).matches("tts-audio/\\d{4}/\\d{2}/[a-f0-9\\-]{36}\\.wav");
    }

    @Test
    @DisplayName("generateObjectKey：format 为 null 时默认使用 wav")
    void generateObjectKey_nullFormat_defaultsToWav() {
        String key = service.generateObjectKey(null);

        assertThat(key).endsWith(".wav");
    }

    @Test
    @DisplayName("generateObjectKey：两次调用生成不同的 Key（UUID 唯一）")
    void generateObjectKey_calledTwice_returnsDifferentKeys() {
        String key1 = service.generateObjectKey("mp3");
        String key2 = service.generateObjectKey("mp3");

        assertThat(key1).isNotEqualTo(key2);
    }

    // -------------------------------------------------------------------------
    // uploadAndSign - 正常场景
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("uploadAndSign：正常上传并返回预签名 URL")
    void uploadAndSign_success_returnPresignedUrl() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn(FAKE_URL);

        String url = service.uploadAndSign("tts-audio/2026/04/xxx.wav", AUDIO_DATA, "wav", null);

        assertThat(url).isEqualTo(FAKE_URL);
        verify(minioClient).putObject(any(PutObjectArgs.class));
        verify(minioClient).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }

    @Test
    @DisplayName("uploadAndSign：自定义 expiryMins 时正常使用")
    void uploadAndSign_defaultExpiry_usesConfiguredValue() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn(FAKE_URL);

        String url = service.uploadAndSign("tts-audio/2026/04/xxx.mp3", AUDIO_DATA, "mp3", null);

        assertThat(url).isEqualTo(FAKE_URL);
        ArgumentCaptor<GetPresignedObjectUrlArgs> captor = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(minioClient).getPresignedObjectUrl(captor.capture());
        assertThat(captor.getValue().expiry()).isEqualTo(ttsProperties.getPresignedExpiryMinutes() * 60);
    }

    @Test
    @DisplayName("uploadAndSign 自定义 expiryMins 时会覆盖默认有效期")
    void uploadAndSign_customExpiry_overridesConfiguredValue() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn(FAKE_URL);

        String url = service.uploadAndSign("tts-audio/2026/04/xxx.mp3", AUDIO_DATA, "mp3", 30);

        assertThat(url).isEqualTo(FAKE_URL);
        ArgumentCaptor<GetPresignedObjectUrlArgs> captor = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(minioClient).getPresignedObjectUrl(captor.capture());
        assertThat(captor.getValue().expiry()).isEqualTo(30 * 60);
    }

    @Test
    @DisplayName("uploadAndSign：PutObject 使用正确的 bucket 和 objectKey")
    void uploadAndSign_putsObjectWithCorrectBucketAndKey() throws Exception {
        String objectKey = "tts-audio/2026/04/test.wav";
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn(FAKE_URL);

        service.uploadAndSign(objectKey, AUDIO_DATA, "wav", null);

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        PutObjectArgs args = captor.getValue();
        assertThat(args.bucket()).isEqualTo(BUCKET);
        assertThat(args.object()).isEqualTo(objectKey);
    }

    // -------------------------------------------------------------------------
    // uploadAndSign - 错误场景
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("uploadAndSign：audioData 为 null 时抛 TTS_AUDIO_UPLOAD_FAILED")
    void uploadAndSign_nullAudioData_throwsUploadFailed() {
        assertThatThrownBy(() ->
                service.uploadAndSign("tts-audio/2026/04/xxx.wav", null, "wav", null))
                .isInstanceOf(TtsException.class)
                .satisfies(ex -> assertThat(((TtsException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TTS_AUDIO_UPLOAD_FAILED));
    }

    @Test
    @DisplayName("uploadAndSign：audioData 为空数组时抛 TTS_AUDIO_UPLOAD_FAILED")
    void uploadAndSign_emptyAudioData_throwsUploadFailed() {
        assertThatThrownBy(() ->
                service.uploadAndSign("tts-audio/2026/04/xxx.wav", new byte[0], "wav", null))
                .isInstanceOf(TtsException.class)
                .satisfies(ex -> assertThat(((TtsException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TTS_AUDIO_UPLOAD_FAILED));
    }

    @Test
    @DisplayName("uploadAndSign：MinIO putObject 抛异常时包装为 TTS_AUDIO_UPLOAD_FAILED")
    void uploadAndSign_putObjectThrows_wrapsAsTtsException() throws Exception {
        doThrow(new RuntimeException("connection refused")).when(minioClient).putObject(any());

        assertThatThrownBy(() ->
                service.uploadAndSign("tts-audio/2026/04/xxx.wav", AUDIO_DATA, "wav", null))
                .isInstanceOf(TtsException.class)
                .satisfies(ex -> assertThat(((TtsException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TTS_AUDIO_UPLOAD_FAILED))
                .hasMessageContaining("connection refused");
    }

    @Test
    @DisplayName("uploadAndSign：getPresignedObjectUrl 抛异常时包装为 TTS_AUDIO_UPLOAD_FAILED")
    void uploadAndSign_presignThrows_wrapsAsTtsException() throws Exception {
        doThrow(new RuntimeException("sign failed")).when(minioClient)
                .getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));

        assertThatThrownBy(() ->
                service.uploadAndSign("tts-audio/2026/04/xxx.wav", AUDIO_DATA, "wav", null))
                .isInstanceOf(TtsException.class)
                .satisfies(ex -> assertThat(((TtsException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TTS_AUDIO_UPLOAD_FAILED))
                .hasMessageContaining("sign failed");
    }
}
