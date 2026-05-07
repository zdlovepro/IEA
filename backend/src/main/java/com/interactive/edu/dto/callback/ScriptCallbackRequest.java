package com.interactive.edu.dto.callback;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ScriptCallbackRequest {

    /**
     * 关联的课件ID
     */
    @NotBlank(message = "课件ID不能为空")
    private String coursewareId;

    /**
     * Python处理状态: SUCCESS / FAILED
     */
    @NotBlank(message = "处理状态不能为空")
    private String processStatus;

    /**
     * 如果处理失败，可携带错误信息
     */
    private String errorMessage;

    /**
     * 课件各页生成的讲稿内容列表
     */
    @Valid
    private List<PageScriptDto> pages;

    @Data
    public static class PageScriptDto {
        @NotNull(message = "页码不能为空")
        private Integer pageIndex;

        /**
         * 这一页的原文本内容
         */
        private String originalText;

        /**
         * 讲稿切割的节点列表 
         */
        @Valid
        private List<ScriptNodeDto> scripts;
    }

    @Data
    public static class ScriptNodeDto {
        /**
         * 唯一节点标识 (例如 n_1_1)
         */
        @NotBlank(message = "讲稿节点ID不能为空")
        private String nodeId;

        /**
         * 讲稿内容
         */
        @NotBlank(message = "讲稿节点内容不能为空")
        private String content;
    }
}
