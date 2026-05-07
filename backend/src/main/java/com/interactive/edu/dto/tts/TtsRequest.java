package com.interactive.edu.dto.tts;

import lombok.Builder;
import lombok.Data;

/**
 * TTS 合成请求参数。
 * <p>
 * 字段均为可选，未填时使用 {@code TtsProperties.Aliyun} 中的默认值。
 */
@Data
@Builder
public class TtsRequest {

    /**
     * 待合成的文本（必填，最多 1000 个字符）
     */
    private String text;

    /**
     * 发音人，如 aixia / aiyu / aijia 等；为 null 时使用配置默认值
     */
    private String voice;

    /**
     * 音频采样率（Hz）；为 null 时使用配置默认值
     */
    private Integer sampleRate;

    /**
     * 音频格式：wav / mp3 / pcm；为 null 时使用配置默认值
     */
    private String format;

    /**
     * 语速控制，-500~500，0 为正常；为 null 时使用配置默认值
     */
    private Integer speechRate;

    /**
     * 语调控制，-500~500，0 为正常；为 null 时使用配置默认值
     */
    private Integer pitchRate;

    /**
     * 音量 0~100；为 null 时使用配置默认值
     */
    private Integer volume;
}
