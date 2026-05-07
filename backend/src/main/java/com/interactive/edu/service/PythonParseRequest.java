package com.interactive.edu.service.python;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PythonParseRequest {
    @JsonProperty("coursewareId")
    private String coursewareId;

    @JsonProperty("storage")
    private String storage;

    @JsonProperty("key")
    private String key;

    @JsonProperty("fileName")
    private String fileName;

    @JsonProperty("contentType")
    private String contentType;
}
