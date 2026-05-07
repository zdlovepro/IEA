package com.interactive.edu.client.impl;

import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.utils.Constants;
import com.interactive.edu.exception.ErrorCode;
import com.interactive.edu.exception.TtsException;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;

@Component
public class DefaultDashScopeSpeechSynthesizerFactory implements DashScopeSpeechSynthesizerFactory {

    @Override
    public DashScopeSpeechSynthesizer create(DashScopeTtsOptions options) throws Exception {
        Constants.baseWebsocketApiUrl = options.websocketUrl();

        SpeechSynthesisParam param = SpeechSynthesisParam.builder()
                .apiKey(options.apiKey())
                .model(options.model())
                .voice(options.voice())
                .format(resolveAudioFormat(options.format(), options.sampleRate()))
                .speechRate(options.speechRate())
                .pitchRate(options.pitchRate())
                .volume(options.volume())
                .build();

        SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, null);
        return new DashScopeSpeechSynthesizer() {
            @Override
            public ByteBuffer call(String text) throws Exception {
                return synthesizer.call(text);
            }

            @Override
            public String getLastRequestId() {
                return synthesizer.getLastRequestId();
            }

            @Override
            public long getFirstPackageDelay() {
                return synthesizer.getFirstPackageDelay();
            }

            @Override
            public void close() {
                if (synthesizer.getDuplexApi() != null) {
                    synthesizer.getDuplexApi().close(1000, "bye");
                }
            }
        };
    }

    static SpeechSynthesisAudioFormat resolveAudioFormat(String format, int sampleRate) {
        return switch (format.toLowerCase()) {
            case "wav" -> switch (sampleRate) {
                case 8000 -> SpeechSynthesisAudioFormat.WAV_8000HZ_MONO_16BIT;
                case 16000 -> SpeechSynthesisAudioFormat.WAV_16000HZ_MONO_16BIT;
                case 22050 -> SpeechSynthesisAudioFormat.WAV_22050HZ_MONO_16BIT;
                case 24000 -> SpeechSynthesisAudioFormat.WAV_24000HZ_MONO_16BIT;
                case 44100 -> SpeechSynthesisAudioFormat.WAV_44100HZ_MONO_16BIT;
                case 48000 -> SpeechSynthesisAudioFormat.WAV_48000HZ_MONO_16BIT;
                default -> throw unsupportedFormat(format, sampleRate);
            };
            case "mp3" -> switch (sampleRate) {
                case 8000 -> SpeechSynthesisAudioFormat.MP3_8000HZ_MONO_128KBPS;
                case 16000 -> SpeechSynthesisAudioFormat.MP3_16000HZ_MONO_128KBPS;
                case 22050 -> SpeechSynthesisAudioFormat.MP3_22050HZ_MONO_256KBPS;
                case 24000 -> SpeechSynthesisAudioFormat.MP3_24000HZ_MONO_256KBPS;
                case 44100 -> SpeechSynthesisAudioFormat.MP3_44100HZ_MONO_256KBPS;
                case 48000 -> SpeechSynthesisAudioFormat.MP3_48000HZ_MONO_256KBPS;
                default -> throw unsupportedFormat(format, sampleRate);
            };
            case "pcm" -> switch (sampleRate) {
                case 8000 -> SpeechSynthesisAudioFormat.PCM_8000HZ_MONO_16BIT;
                case 16000 -> SpeechSynthesisAudioFormat.PCM_16000HZ_MONO_16BIT;
                case 22050 -> SpeechSynthesisAudioFormat.PCM_22050HZ_MONO_16BIT;
                case 24000 -> SpeechSynthesisAudioFormat.PCM_24000HZ_MONO_16BIT;
                case 44100 -> SpeechSynthesisAudioFormat.PCM_44100HZ_MONO_16BIT;
                case 48000 -> SpeechSynthesisAudioFormat.PCM_48000HZ_MONO_16BIT;
                default -> throw unsupportedFormat(format, sampleRate);
            };
            default -> throw unsupportedFormat(format, sampleRate);
        };
    }

    private static TtsException unsupportedFormat(String format, int sampleRate) {
        return new TtsException(
                ErrorCode.TTS_INVALID_REQUEST,
                "DashScope 不支持格式/采样率组合：" + format + "/" + sampleRate);
    }
}
