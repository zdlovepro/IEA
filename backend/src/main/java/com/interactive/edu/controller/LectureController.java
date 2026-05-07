package com.interactive.edu.controller;

import com.interactive.edu.dto.BaseResponse;
import com.interactive.edu.service.lecture.LectureService;
import com.interactive.edu.vo.lecture.LectureSessionView;
import com.interactive.edu.vo.lecture.SessionStatusView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lecture")
@RequiredArgsConstructor
public class LectureController {

    private final LectureService lectureService;

    @PostMapping("/start")
    public BaseResponse<LectureSessionView> start(@Valid @RequestBody StartLectureRequest request) {
        return BaseResponse.ok(lectureService.startLecture(request.coursewareId(), request.userId()));
    }

    @PostMapping("/{sessionId}/pause")
    public BaseResponse<SessionStatusView> pause(@PathVariable("sessionId") @NotBlank String sessionId) {
        return BaseResponse.ok(lectureService.pause(sessionId));
    }

    @PostMapping("/pause")
    public BaseResponse<SessionStatusView> pauseByBody(@Valid @RequestBody ResumeLectureRequest request) {
        return BaseResponse.ok(lectureService.pause(request.sessionId()));
    }

    @PostMapping("/resume")
    public BaseResponse<LectureSessionView> resume(@Valid @RequestBody ResumeLectureRequest request) {
        return BaseResponse.ok(lectureService.resume(request.sessionId()));
    }

    public record StartLectureRequest(
            @NotBlank(message = "coursewareId 不能为空") String coursewareId,
            String userId
    ) {
    }

    public record ResumeLectureRequest(
            @NotBlank(message = "sessionId 不能为空") String sessionId
    ) {
    }
}
