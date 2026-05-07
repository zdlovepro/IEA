package com.interactive.edu.client.impl;

import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.interactive.edu.exception.TtsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultDashScopeSpeechSynthesizerFactoryTest {

    @Test
    @DisplayName("mp3 24000Hz 会映射到 DashScope MP3 24k 枚举")
    void resolveAudioFormat_mp324000_returnsExpectedEnum() {
        assertThat(DefaultDashScopeSpeechSynthesizerFactory.resolveAudioFormat("mp3", 24000))
                .isEqualTo(SpeechSynthesisAudioFormat.MP3_24000HZ_MONO_256KBPS);
    }

    @Test
    @DisplayName("wav 16000Hz 会映射到 DashScope WAV 16k 枚举")
    void resolveAudioFormat_wav16000_returnsExpectedEnum() {
        assertThat(DefaultDashScopeSpeechSynthesizerFactory.resolveAudioFormat("wav", 16000))
                .isEqualTo(SpeechSynthesisAudioFormat.WAV_16000HZ_MONO_16BIT);
    }

    @Test
    @DisplayName("pcm 8000Hz 会映射到 DashScope PCM 8k 枚举")
    void resolveAudioFormat_pcm8000_returnsExpectedEnum() {
        assertThat(DefaultDashScopeSpeechSynthesizerFactory.resolveAudioFormat("pcm", 8000))
                .isEqualTo(SpeechSynthesisAudioFormat.PCM_8000HZ_MONO_16BIT);
    }

    @Test
    @DisplayName("不支持的格式或采样率组合会抛出 TtsException")
    void resolveAudioFormat_invalidCombination_throwsException() {
        assertThatThrownBy(() ->
                DefaultDashScopeSpeechSynthesizerFactory.resolveAudioFormat("aac", 16000))
                .isInstanceOf(TtsException.class)
                .hasMessageContaining("不支持");
    }
}
