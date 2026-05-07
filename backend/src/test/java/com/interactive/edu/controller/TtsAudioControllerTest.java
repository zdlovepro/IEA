package com.interactive.edu.controller;

import com.interactive.edu.config.StorageProperties;
import com.interactive.edu.storage.LocalTtsAudioStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TtsAudioControllerTest {

    @TempDir
    Path tempDir;

    private LocalTtsAudioStorageService storageService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.setLocalBaseDir(tempDir.toString());
        storageService = new LocalTtsAudioStorageService(properties);
        mockMvc = MockMvcBuilders.standaloneSetup(new TtsAudioController(properties)).build();
    }

    @Test
    @DisplayName("local 模式返回的 wav audioUrl 可访问，且 Content-Type 为 audio/wav")
    void getAudio_wav_returnsFileWithWaveContentType() throws Exception {
        byte[] audioData = new byte[]{0x52, 0x49, 0x46, 0x46};
        String audioUrl = storageService.uploadAndSign("tts-audio/2026/05/demo.wav", audioData, "wav", null);

        mockMvc.perform(get(audioUrl))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=3600"))
                .andExpect(content().contentType("audio/wav"))
                .andExpect(content().bytes(audioData));
    }

    @Test
    @DisplayName("local 模式返回的 mp3 audioUrl 可访问，且 Content-Type 为 audio/mpeg")
    void getAudio_mp3_returnsFileWithMpegContentType() throws Exception {
        byte[] audioData = new byte[]{0x49, 0x44, 0x33};
        String audioUrl = storageService.uploadAndSign("tts-audio/2026/05/demo.mp3", audioData, "mp3", null);

        mockMvc.perform(get(audioUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(content().bytes(audioData));
    }
}
