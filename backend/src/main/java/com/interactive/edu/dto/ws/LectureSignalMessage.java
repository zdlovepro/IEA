package com.interactive.edu.dto.ws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class LectureSignalMessage {

    private String type;
    private String sessionId;
    private Integer pageIndex;
    private Double currentTime;
    private Map<String, Object> payload = new LinkedHashMap<>();
}
