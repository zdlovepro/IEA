package com.interactive.edu.dto.asr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsrRequest {

    private byte[] audioData;
    private String filename;
    private String contentType;
    private String sessionId;
    private Integer pageIndex;

    public int getAudioSize() {
        return audioData == null ? 0 : audioData.length;
    }
}
