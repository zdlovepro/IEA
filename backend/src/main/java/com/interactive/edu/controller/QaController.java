package com.interactive.edu.controller;

import com.interactive.edu.dto.BaseResponse;
import com.interactive.edu.service.qa.QaService;
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
    public BaseResponse<QaService.QaAnswerView> askText(@RequestBody AskTextRequest request) {
        return BaseResponse.ok(qaService.askText(request.sessionId(), request.question()));
    }

    public record AskTextRequest(String sessionId, String question) {
    }
}
