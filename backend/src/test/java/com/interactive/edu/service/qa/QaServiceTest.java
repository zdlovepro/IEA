package com.interactive.edu.service.qa;

import com.interactive.edu.exception.ErrorCode;
import com.interactive.edu.exception.ServiceException;
import com.interactive.edu.service.courseware.CoursewareService;
import com.interactive.edu.service.lecture.LectureService;
import com.interactive.edu.service.python.PythonQaClient;
import com.interactive.edu.service.python.PythonQaRequest;
import com.interactive.edu.service.python.PythonQaResponse;
import com.interactive.edu.vo.courseware.ScriptSegmentView;
import com.interactive.edu.vo.qa.QaAnswerView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QaServiceTest {

    @Mock
    private LectureService lectureService;

    @Mock
    private CoursewareService coursewareService;

    @Mock
    private PythonQaClient pythonQaClient;

    @InjectMocks
    private QaService qaService;

    @Test
    @DisplayName("uses Python RAG answer when Python QA succeeds")
    void askText_pythonSuccess_returnsPythonAnswer() {
        LectureService.SessionSnapshot session = new LectureService.SessionSnapshot(
                "sess_qa_1",
                "cware_qa_1",
                "user_1",
                3,
                "PLAYING"
        );
        when(lectureService.getSessionSnapshot("sess_qa_1")).thenReturn(session);
        when(pythonQaClient.askText(any(PythonQaRequest.class))).thenReturn(
                new PythonQaResponse(
                        "这是 Python RAG 的回答。",
                        List.of(new PythonQaResponse.EvidencePayload("page_3", "第 3 页证据", 3, "chunk_3")),
                        123
                )
        );

        QaAnswerView result = qaService.askText("sess_qa_1", "这一页在讲什么");

        assertThat(result.answer()).isEqualTo("这是 Python RAG 的回答。");
        assertThat(result.latencyMs()).isEqualTo(123);
        assertThat(result.evidence()).hasSize(1);
        assertThat(result.evidence().get(0).source()).isEqualTo("page_3");
        assertThat(result.evidence().get(0).pageIndex()).isEqualTo(3);
        assertThat(result.evidence().get(0).chunkId()).isEqualTo("chunk_3");

        ArgumentCaptor<PythonQaRequest> captor = ArgumentCaptor.forClass(PythonQaRequest.class);
        verify(pythonQaClient).askText(captor.capture());
        assertThat(captor.getValue().getSessionId()).isEqualTo("sess_qa_1");
        assertThat(captor.getValue().getCoursewareId()).isEqualTo("cware_qa_1");
        assertThat(captor.getValue().getPageIndex()).isEqualTo(3);
        assertThat(captor.getValue().getQuestion()).isEqualTo("这一页在讲什么");
        assertThat(captor.getValue().getTopK()).isEqualTo(5);
        verifyNoInteractions(coursewareService);
    }

    @Test
    @DisplayName("falls back to local template when Python QA times out")
    void askText_pythonTimeout_fallsBackToLocalAnswer() {
        LectureService.SessionSnapshot session = new LectureService.SessionSnapshot(
                "sess_qa_2",
                "cware_qa_2",
                "user_2",
                2,
                "PLAYING"
        );
        ScriptSegmentView target = new ScriptSegmentView(
                "seg_1",
                "node_1",
                1,
                "递归定义",
                "递归需要先明确终止条件，再设计递归关系。",
                List.of("终止条件", "递归关系"),
                null
        );
        ScriptSegmentView current = new ScriptSegmentView(
                "seg_2",
                "node_2",
                2,
                "执行过程",
                "这一页介绍调用栈如何展开和返回。",
                List.of("调用栈"),
                null
        );

        when(lectureService.getSessionSnapshot("sess_qa_2")).thenReturn(session);
        when(pythonQaClient.askText(any(PythonQaRequest.class)))
                .thenThrow(new ServiceException(ErrorCode.PYTHON_SERVICE_ERROR, "timeout"));
        when(coursewareService.getScriptSegments("cware_qa_2")).thenReturn(List.of(target, current));
        when(coursewareService.getSegmentForPage("cware_qa_2", 2)).thenReturn(current);

        QaAnswerView result = qaService.askText("sess_qa_2", "什么是终止条件");

        assertThat(result.answer()).contains("递归定义", "终止条件");
        assertThat(result.latencyMs()).isGreaterThanOrEqualTo(1);
        assertThat(result.evidence()).hasSize(2);
        assertThat(result.evidence().get(0).pageIndex()).isEqualTo(1);
        assertThat(result.evidence().get(1).pageIndex()).isEqualTo(2);
    }

    @Test
    @DisplayName("falls back to local template when Python QA returns error")
    void askText_pythonError_fallsBackToLocalAnswer() {
        LectureService.SessionSnapshot session = new LectureService.SessionSnapshot(
                "sess_qa_3",
                "cware_qa_3",
                "user_3",
                1,
                "PLAYING"
        );
        ScriptSegmentView current = new ScriptSegmentView(
                "seg_3",
                "node_3",
                1,
                "链表结构",
                "链表由节点和指针组成，每个节点会指向下一个节点。",
                List.of("节点", "指针"),
                null
        );

        when(lectureService.getSessionSnapshot("sess_qa_3")).thenReturn(session);
        when(pythonQaClient.askText(any(PythonQaRequest.class)))
                .thenThrow(new ServiceException(ErrorCode.PYTHON_SERVICE_ERROR, "python error"));
        when(coursewareService.getScriptSegments("cware_qa_3")).thenReturn(List.of(current));
        when(coursewareService.getSegmentForPage("cware_qa_3", 1)).thenReturn(current);

        QaAnswerView result = qaService.askText("sess_qa_3", "链表是什么");

        assertThat(result.answer()).contains("链表结构", "链表由节点和指针组成");
        assertThat(result.evidence()).hasSize(1);
        assertThat(result.evidence().get(0).chunkId()).isEqualTo("node_3");
    }

    @Test
    @DisplayName("throws when session does not exist")
    void askText_sessionNotFound_throwsNoSuchElementException() {
        when(lectureService.getSessionSnapshot("missing")).thenThrow(new NoSuchElementException("session missing"));

        assertThatThrownBy(() -> qaService.askText("missing", "问题"))
                .isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(pythonQaClient, coursewareService);
    }
}
