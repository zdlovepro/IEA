package com.interactive.edu.controller;

import com.interactive.edu.exception.BusinessException;
import com.interactive.edu.exception.ErrorCode;
import com.interactive.edu.exception.GlobalExceptionHandler;
import com.interactive.edu.service.lecture.LectureService;
import com.interactive.edu.service.record.LectureRecordService;
import com.interactive.edu.vo.record.InterruptRecordView;
import com.interactive.edu.vo.record.QaRecordView;
import com.interactive.edu.vo.qa.EvidenceItemView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LectureControllerTest {

    @Mock
    private LectureService lectureService;

    @Mock
    private LectureRecordService lectureRecordService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LectureController(lectureService, lectureRecordService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("get records returns interrupts and qa records")
    void getRecords_success() throws Exception {
        when(lectureRecordService.getSessionRecords("sess_record_api")).thenReturn(
                new LectureRecordService.SessionRecordsSnapshot(
                        List.of(new InterruptRecordView(
                                "intr_1",
                                "sess_record_api",
                                "cware_api",
                                2,
                                18.6,
                                "老师这里没听清",
                                "ANSWERED",
                                Instant.parse("2026-05-10T06:00:00Z"),
                                Instant.parse("2026-05-10T06:00:05Z")
                        )),
                        List.of(new QaRecordView(
                                "qa_1",
                                "sess_record_api",
                                "cware_api",
                                2,
                                "这一页在讲什么",
                                "这一页主要在讲递归终止条件。",
                                List.of(new EvidenceItemView("page_2", "递归需要先明确终止条件。", 2, "node_2")),
                                123,
                                Instant.parse("2026-05-10T06:00:10Z")
                        ))
                )
        );

        mockMvc.perform(get("/api/v1/lecture/sess_record_api/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.interrupts[0].interruptId").value("intr_1"))
                .andExpect(jsonPath("$.data.interrupts[0].asrText").value("老师这里没听清"))
                .andExpect(jsonPath("$.data.qaRecords[0].qaRecordId").value("qa_1"))
                .andExpect(jsonPath("$.data.qaRecords[0].evidence[0].pageIndex").value(2));
    }

    @Test
    @DisplayName("missing session returns not found response")
    void getRecords_missingSession_returnsNotFound() throws Exception {
        when(lectureRecordService.getSessionRecords("missing"))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "lecture session not found"));

        mockMvc.perform(get("/api/v1/lecture/missing/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.message").value("lecture session not found"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
