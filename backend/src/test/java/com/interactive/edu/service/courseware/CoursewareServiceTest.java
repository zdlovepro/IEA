package com.interactive.edu.service.courseware;

import com.interactive.edu.dto.CoursewareUploadResult;
import com.interactive.edu.enums.CoursewareStatus;
import com.interactive.edu.enums.TaskStatus;
import com.interactive.edu.exception.ErrorCode;
import com.interactive.edu.exception.ServiceException;
import com.interactive.edu.service.python.PythonParseClient;
import com.interactive.edu.service.python.PythonParseRequest;
import com.interactive.edu.service.python.PythonScriptClient;
import com.interactive.edu.service.python.PythonScriptRequest;
import com.interactive.edu.service.storage.StorageService;
import com.interactive.edu.service.storage.StorageServiceFactory;
import com.interactive.edu.service.storage.StoredObject;
import com.interactive.edu.service.tts.TtsService;
import com.interactive.edu.vo.courseware.CoursewareDetailView;
import com.interactive.edu.vo.courseware.ScriptView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoursewareServiceTest {

    @Mock
    private StorageServiceFactory storageServiceFactory;

    @Mock
    private StorageService storageService;

    @Mock
    private PythonParseClient pythonParseClient;

    @Mock
    private PythonScriptClient pythonScriptClient;

    @Mock
    private TtsService ttsService;

    private RecordingTaskExecutor taskExecutor;
    private CoursewareService service;

    @BeforeEach
    void setUp() {
        taskExecutor = new RecordingTaskExecutor();
        service = new CoursewareService(storageServiceFactory, pythonParseClient, pythonScriptClient, taskExecutor, ttsService);

        when(storageServiceFactory.get()).thenReturn(storageService);
        when(storageService.save(anyString(), any())).thenAnswer(invocation ->
                new StoredObject(invocation.getArgument(0) + "/demo.pdf", "local"));
        when(pythonParseClient.parse(any(PythonParseRequest.class))).thenReturn(new PythonParseClient.ParsePayload(
                2,
                List.of("第一页标题", "第二页标题"),
                List.of(
                        new PythonParseClient.ParseSegment(1, "第一页标题", "第一页正文", List.of("概念1")),
                        new PythonParseClient.ParseSegment(2, "第二页标题", "第二页正文", List.of("概念2"))
                )
        ));
    }

    @Test
    @DisplayName("脚本生成任务已在执行中时不重复提交，接口可立即返回 GENERATING_SCRIPT")
    void triggerScriptGeneration_whenAlreadyGenerating_doesNotSubmitDuplicateTask() {
        String coursewareId = uploadAndFinishParse();

        String firstStatus = service.triggerScriptGeneration(coursewareId);
        CoursewareDetailView generatingDetail = service.getDetail(coursewareId);

        assertThat(firstStatus).isEqualTo(CoursewareStatus.GENERATING_SCRIPT.name());
        assertThat(generatingDetail.status()).isEqualTo(CoursewareStatus.GENERATING_SCRIPT.name());
        assertThat(generatingDetail.currentTaskStatus()).isEqualTo(TaskStatus.RUNNING.name());
        assertThat(taskExecutor.size()).isEqualTo(1);
        verifyNoInteractions(pythonScriptClient);

        String secondStatus = service.triggerScriptGeneration(coursewareId);

        assertThat(secondStatus).isEqualTo(CoursewareStatus.GENERATING_SCRIPT.name());
        assertThat(taskExecutor.size()).isEqualTo(1);
        assertThat(service.getScript(coursewareId)).isNull();
    }

    @Test
    @DisplayName("Python 可用时优先使用 Python 生成的 opening/pages/closing，并最终进入 READY")
    void triggerScriptGeneration_pythonAvailable_usesPythonResultAndReady() {
        when(pythonScriptClient.generate(any(PythonScriptRequest.class))).thenReturn(
                new PythonScriptClient.ScriptPayload(
                        "ignored-courseware-id",
                        "统一开场白",
                        List.of(
                                new PythonScriptClient.PageScriptPayload(1, "第一页讲稿", "继续到第二页"),
                                new PythonScriptClient.PageScriptPayload(2, "第二页讲稿", "本页结束，准备总结")
                        ),
                        "统一结语"
                )
        );

        String coursewareId = uploadAndFinishParse();

        assertThat(service.triggerScriptGeneration(coursewareId)).isEqualTo(CoursewareStatus.GENERATING_SCRIPT.name());
        taskExecutor.runNext();

        ScriptView script = service.getScript(coursewareId);
        CoursewareDetailView detail = service.getDetail(coursewareId);

        assertThat(detail.status()).isEqualTo(CoursewareStatus.READY.name());
        assertThat(detail.currentTaskStatus()).isEqualTo(TaskStatus.SUCCESS.name());
        assertThat(script).isNotNull();
        assertThat(script.status()).isEqualTo(CoursewareStatus.READY.name());
        assertThat(script.opening()).isEqualTo("统一开场白");
        assertThat(script.closing()).isEqualTo("统一结语");
        assertThat(script.segments()).hasSize(2);
        assertThat(script.segments().get(0).content()).contains("统一开场白", "第一页讲稿", "继续到第二页");
        assertThat(script.segments().get(1).content()).contains("第二页讲稿", "本页结束，准备总结", "统一结语");

        ArgumentCaptor<PythonScriptRequest> captor = ArgumentCaptor.forClass(PythonScriptRequest.class);
        verify(pythonScriptClient).generate(captor.capture());
        assertThat(captor.getValue().getCoursewareId()).isEqualTo(coursewareId);
        assertThat(captor.getValue().getCoursewareName()).isEqualTo("示例课件");
        assertThat(captor.getValue().getPages()).hasSize(2);

        assertThat(service.triggerScriptGeneration(coursewareId)).isEqualTo(CoursewareStatus.READY.name());
        verify(pythonScriptClient, times(1)).generate(any(PythonScriptRequest.class));
    }

    @Test
    @DisplayName("Python 不可用时仍使用本地 fallback 生成讲稿，并最终进入 READY")
    void triggerScriptGeneration_pythonUnavailable_fallsBackToLocalDraft() {
        when(pythonScriptClient.generate(any(PythonScriptRequest.class)))
                .thenThrow(new ServiceException(ErrorCode.PYTHON_SERVICE_ERROR, "python down"));

        String coursewareId = uploadAndFinishParse();

        assertThat(service.triggerScriptGeneration(coursewareId)).isEqualTo(CoursewareStatus.GENERATING_SCRIPT.name());
        taskExecutor.runNext();

        ScriptView script = service.getScript(coursewareId);
        CoursewareDetailView detail = service.getDetail(coursewareId);

        assertThat(detail.status()).isEqualTo(CoursewareStatus.READY.name());
        assertThat(detail.currentTaskStatus()).isEqualTo(TaskStatus.SUCCESS.name());
        assertThat(script).isNotNull();
        assertThat(script.opening()).contains("示例课件");
        assertThat(script.segments().get(0).content()).contains("第一页正文");
        assertThat(script.segments().get(0).content()).contains("理解了这一页之后");
        assertThat(script.closing()).contains("主要内容");
    }

    private String uploadAndFinishParse() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "demo.pdf",
                "application/pdf",
                "demo".getBytes(StandardCharsets.UTF_8)
        );

        CoursewareUploadResult uploadResult = service.upload(file, "示例课件");
        assertThat(taskExecutor.size()).isEqualTo(1);
        taskExecutor.runNext();

        CoursewareDetailView detail = service.getDetail(uploadResult.getCoursewareId());
        assertThat(detail.status()).isEqualTo(CoursewareStatus.PARSED.name());
        assertThat(detail.currentTaskStatus()).isEqualTo(TaskStatus.SUCCESS.name());
        return uploadResult.getCoursewareId();
    }

    private static final class RecordingTaskExecutor implements TaskExecutor {

        private final Deque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.addLast(task);
        }

        private int size() {
            return tasks.size();
        }

        private void runNext() {
            Runnable task = tasks.pollFirst();
            if (task != null) {
                task.run();
            }
        }
    }
}
