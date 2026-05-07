package com.interactive.edu.storage;

/**
 * TTS 音频对象存储服务。
 * <p>
 * 负责将 TTS 合成产生的音频字节流上传至对象存储（MinIO / S3），
 * 并生成带有效期的预签名直链供客户端播放。
 * <p>
 * 对象 Key 推荐格式：{@code tts-audio/{yyyy}/{MM}/{uuid}.{format}}，
 * 由调用方通过 {@link #generateObjectKey(String)} 生成，也可自行传入。
 */
public interface TtsAudioStorageService {

    /**
     * 将音频数据上传至对象存储，并返回带有效期的预签名 GET 直链。
     *
     * @param objectKey  对象在 bucket 内的完整路径，如 {@code tts-audio/2024/05/xxx.wav}
     * @param audioData  TTS 合成产生的音频二进制数据，不得为 null 或空
     * @param format     音频格式（{@code wav} / {@code mp3} / {@code pcm}），用于推断 Content-Type
     * @param expiryMins 直链有效期（分钟）；传 {@code null} 则使用配置项 {@code tts.presigned-expiry-minutes}
     * @return 预签名 GET URL，客户端可直接访问播放
     * @throws com.interactive.edu.exception.TtsException 当上传或签名失败时，携带
     *         {@link com.interactive.edu.exception.ErrorCode#TTS_AUDIO_UPLOAD_FAILED}
     */
    String uploadAndSign(String objectKey, byte[] audioData, String format, Integer expiryMins);

    /**
     * 生成规范化的对象 Key。
     * <p>
     * 格式：{@code tts-audio/{yyyy}/{MM}/{uuid}.{format}}，确保不同日期的文件自动归档。
     *
     * @param format 音频格式，如 {@code wav}
     * @return 唯一对象 Key
     */
    String generateObjectKey(String format);
}
