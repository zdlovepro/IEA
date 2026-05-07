package com.interactive.edu.enums;

/**
 * TTS 云厂商提供商枚举。
 * <p>
 * 对应 {@code tts.provider} 配置项。
 */
public enum TtsProvider {

    /** 阿里云 NLS 语音合成 */
    ALIYUN("aliyun");

    private final String value;

    TtsProvider(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
