package com.interactive.edu.service.python;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PythonScriptRequest {

    @JsonProperty("coursewareId")
    private String coursewareId;

    @JsonProperty("coursewareName")
    private String coursewareName;

    @JsonProperty("subject")
    private String subject;

    @JsonProperty("pages")
    private List<PageContent> pages;

    @JsonProperty("callbackUrl")
    private String callbackUrl;

    @Data
    @AllArgsConstructor
    public static class PageContent {

        @JsonProperty("pageIndex")
        private int pageIndex;

        @JsonProperty("title")
        private String title;

        @JsonProperty("textContent")
        private String textContent;

        @JsonProperty("keywords")
        private List<String> keywords;
    }
}
