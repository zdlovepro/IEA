package com.interactive.edu.dto.tts;

import lombok.Builder;
import lombok.Data;

/**
 * TTS 合成结果。
 */
@Data
@Builder
public class TtsResult {

    /**
     * 合成后的音频二进制数据
     */
    private byte[] audioData;

    /**
     * 实际音频格式（与请求中 format 一致）
     */
    private String format;

    /**
     * 实际采样率（Hz）
     */
    private int sampleRate;

    /**
     * 服务端请求 ID，用于问题排查（来自 X-NLS-RequestId 响应头）
     */
    private String requestId;
}
