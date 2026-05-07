package com.interactive.edu.controller;

import com.interactive.edu.dto.BaseResponse;
import com.interactive.edu.service.qa.QaService;
import com.interactive.edu.vo.qa.QaAnswerView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/qa")
@RequiredArgsConstructor
public class QaController {

    private final QaService qaService;

    @PostMapping("/ask-text")
    public BaseResponse<QaAnswerView> askText(@Valid @RequestBody AskTextRequest request) {
        return BaseResponse.ok(qaService.askText(request.sessionId(), request.question()));
    }

    public record AskTextRequest(
            @NotBlank(message = "sessionId 不能为空") String sessionId,
            @NotBlank(message = "question 不能为空") String question
    ) {
    }
}
