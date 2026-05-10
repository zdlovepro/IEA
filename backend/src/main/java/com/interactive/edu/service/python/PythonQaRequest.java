package com.interactive.edu.service.python;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PythonQaRequest {

    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("coursewareId")
    private String coursewareId;

    @JsonProperty("pageIndex")
    private Integer pageIndex;

    @JsonProperty("question")
    private String question;

    @JsonProperty("topK")
    private Integer topK;
}
