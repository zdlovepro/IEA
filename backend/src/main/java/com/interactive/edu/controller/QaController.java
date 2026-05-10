package com.interactive.edu.controller;

import com.interactive.edu.dto.BaseResponse;
import com.interactive.edu.service.qa.QaService;
import com.interactive.edu.vo.qa.QaAnswerView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/qa")
@RequiredArgsConstructor
@Validated
public class QaController {

    private final QaService qaService;

    @PostMapping("/ask-text")
    public BaseResponse<QaAnswerView> askText(@Valid @RequestBody AskTextRequest request) {
        return BaseResponse.ok(qaService.askText(request.sessionId(), request.question()));
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamText(
            @RequestParam @NotBlank(message = "sessionId 不能为空") String sessionId,
            @RequestParam @NotBlank(message = "question 不能为空") String question,
            @RequestParam(required = false) @Min(value = 1, message = "topK 必须大于 0") Integer topK
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(qaService.streamText(sessionId, question, topK));
    }

    public record AskTextRequest(
            @NotBlank(message = "sessionId 不能为空") String sessionId,
            @NotBlank(message = "question 不能为空") String question
    ) {
    }
}
