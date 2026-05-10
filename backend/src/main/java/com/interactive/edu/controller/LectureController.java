package com.interactive.edu.controller;

import com.interactive.edu.dto.BaseResponse;
import com.interactive.edu.service.lecture.LectureService;
import com.interactive.edu.service.record.LectureRecordService;
import com.interactive.edu.vo.lecture.LectureSessionView;
import com.interactive.edu.vo.lecture.SessionStatusView;
import com.interactive.edu.vo.record.InterruptRecordView;
import com.interactive.edu.vo.record.QaRecordView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/lecture")
@RequiredArgsConstructor
@Validated
public class LectureController {

    private final LectureService lectureService;
    private final LectureRecordService lectureRecordService;

    @PostMapping("/start")
    public BaseResponse<LectureSessionView> start(@Valid @RequestBody StartLectureRequest request) {
        LectureSessionView sessionView = lectureService.startLecture(request.coursewareId(), request.userId());
        lectureRecordService.registerSession(sessionView.sessionId());
        return BaseResponse.ok(sessionView);
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
        LectureSessionView sessionView = lectureService.resume(request.sessionId());
        lectureRecordService.tryMarkLatestInterruptResumed(request.sessionId());
        return BaseResponse.ok(sessionView);
    }

    @GetMapping("/{sessionId}/records")
    public BaseResponse<LectureRecordsResponse> getRecords(@PathVariable("sessionId") @NotBlank String sessionId) {
        LectureRecordService.SessionRecordsSnapshot snapshot = lectureRecordService.getSessionRecords(sessionId);
        return BaseResponse.ok(new LectureRecordsResponse(snapshot.interrupts(), snapshot.qaRecords()));
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

    public record LectureRecordsResponse(
            List<InterruptRecordView> interrupts,
            List<QaRecordView> qaRecords
    ) {
    }
}
