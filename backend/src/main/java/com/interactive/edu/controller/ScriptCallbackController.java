package com.interactive.edu.controller;

import com.interactive.edu.dto.BaseResponse;
import com.interactive.edu.dto.callback.ScriptCallbackRequest;
import com.interactive.edu.service.courseware.ScriptCallbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@Profile({"full", "prod"})
@ConditionalOnBean(ScriptCallbackService.class)
@RequestMapping("/api/v1/courseware/callback")
@RequiredArgsConstructor
public class ScriptCallbackController {

    private final ScriptCallbackService scriptCallbackService;

    /**
     * 接收 Python 异步大模型解析讲稿的返回数据
     * 确保接收方和发送方契约一致：
     * POST /api/v1/courseware/callback
     */
    @PostMapping
    public BaseResponse<String> receiveScriptCallback(@Valid @RequestBody ScriptCallbackRequest request) {
        log.info("收到讲稿生成回调请求: coursewareId={}, status={}", request.getCoursewareId(), request.getProcessStatus());
        scriptCallbackService.processScriptCallback(request);
        return BaseResponse.ok("回调处理成功，数据已落库");
    }
}
