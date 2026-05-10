package com.interactive.edu.service.record;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interactive.edu.exception.BusinessException;
import com.interactive.edu.exception.ErrorCode;
import com.interactive.edu.vo.qa.EvidenceItemView;
import com.interactive.edu.vo.record.InterruptRecordView;
import com.interactive.edu.vo.record.QaRecordView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LectureRecordServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("stores interrupt and qa records in memory and jsonl fallback")
    void createAndLoadRecords_success() throws Exception {
        LectureRecordService service = new LectureRecordService(newObjectMapper(), tempDir);
        service.registerSession("sess_record_1");

        InterruptRecordView interrupt = service.createInterruptRecord("sess_record_1", "cware_record_1", 2, 16.8);
        InterruptRecordView withAsr = service.updateLatestInterruptAsrText("sess_record_1", "老师，这里我没听清");
        QaRecordView qaRecord = service.createQaRecord(
                "sess_record_1",
                "cware_record_1",
                2,
                "这一页在讲什么",
                "这一页主要在讲递归的终止条件。",
                List.of(new EvidenceItemView("page_2", "递归需要先明确终止条件。", 2, "node_2")),
                135
        );
        service.tryMarkLatestInterruptResumed("sess_record_1");

        LectureRecordService.SessionRecordsSnapshot snapshot = service.getSessionRecords("sess_record_1");

        assertThat(snapshot.interrupts()).hasSize(1);
        assertThat(snapshot.qaRecords()).hasSize(1);
        assertThat(snapshot.interrupts().get(0).interruptId()).isEqualTo(interrupt.interruptId());
        assertThat(snapshot.interrupts().get(0).asrText()).isEqualTo("老师，这里我没听清");
        assertThat(snapshot.interrupts().get(0).status()).isEqualTo("RESUMED");
        assertThat(snapshot.qaRecords().get(0).qaRecordId()).isEqualTo(qaRecord.qaRecordId());
        assertThat(snapshot.qaRecords().get(0).evidence()).hasSize(1);

        Path jsonl = tempDir.resolve("sess_record_1.jsonl");
        assertThat(Files.exists(jsonl)).isTrue();
        assertThat(Files.readAllLines(jsonl)).hasSize(5);

        LectureRecordService recoveredService = new LectureRecordService(newObjectMapper(), tempDir);
        LectureRecordService.SessionRecordsSnapshot recovered = recoveredService.getSessionRecords("sess_record_1");

        assertThat(recovered.interrupts()).hasSize(1);
        assertThat(recovered.interrupts().get(0).interruptId()).isEqualTo(withAsr.interruptId());
        assertThat(recovered.interrupts().get(0).status()).isEqualTo("RESUMED");
        assertThat(recovered.qaRecords()).hasSize(1);
        assertThat(recovered.qaRecords().get(0).answer()).isEqualTo("这一页主要在讲递归的终止条件。");
    }

    @Test
    @DisplayName("write file failure does not break main flow")
    void persistFailure_doesNotBreakMainFlow() throws Exception {
        Path blockedFile = Files.createTempFile(tempDir, "records", ".tmp");
        LectureRecordService service = new LectureRecordService(newObjectMapper(), blockedFile);
        service.registerSession("sess_record_2");

        service.createInterruptRecord("sess_record_2", "cware_record_2", 1, 3.2);
        service.createQaRecord(
                "sess_record_2",
                "cware_record_2",
                1,
                "这页在讲什么",
                "这页在讲链表的基本结构。",
                List.of(),
                88
        );

        LectureRecordService.SessionRecordsSnapshot snapshot = service.getSessionRecords("sess_record_2");
        assertThat(snapshot.interrupts()).hasSize(1);
        assertThat(snapshot.qaRecords()).hasSize(1);
    }

    @Test
    @DisplayName("blank sessionId returns param error")
    void getSessionRecords_blankSessionId_throwsBusinessException() {
        LectureRecordService service = new LectureRecordService(newObjectMapper(), tempDir);

        assertThatThrownBy(() -> service.getSessionRecords(" "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PARAM_ERROR);
    }

    @Test
    @DisplayName("unknown session returns not found")
    void getSessionRecords_unknownSession_throwsBusinessException() {
        LectureRecordService service = new LectureRecordService(newObjectMapper(), tempDir);

        assertThatThrownBy(() -> service.getSessionRecords("missing"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private ObjectMapper newObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
