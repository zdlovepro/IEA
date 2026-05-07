package com.interactive.edu.service.courseware;

import com.interactive.edu.dto.callback.ScriptCallbackRequest;
import com.interactive.edu.entity.Courseware;
import com.interactive.edu.entity.LectureScript;
import com.interactive.edu.enums.CoursewareStatus;
import com.interactive.edu.repository.CoursewarePageRepository;
import com.interactive.edu.repository.CoursewareRepository;
import com.interactive.edu.repository.LectureScriptRepository;
import com.interactive.edu.service.tts.TtsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScriptCallbackServiceTest {

    @Mock
    private CoursewareRepository coursewareRepository;

    @Mock
    private CoursewarePageRepository coursewarePageRepository;

    @Mock
    private LectureScriptRepository lectureScriptRepository;

    @Mock
    private TtsService ttsService;

    private ScriptCallbackService service;

    @BeforeEach
    void setUp() {
        service = new ScriptCallbackService(coursewareRepository, coursewarePageRepository, lectureScriptRepository, ttsService);
    }

    @Test
    @DisplayName("重复成功回调到达时幂等忽略，避免重复插入脚本")
    void processScriptCallback_duplicateSuccess_isIgnored() {
        Courseware courseware = new Courseware();
        courseware.setId("cware_ready");
        courseware.setStatus(CoursewareStatus.READY.name());

        when(coursewareRepository.findById("cware_ready")).thenReturn(Optional.of(courseware));
        when(lectureScriptRepository.findByCoursewareIdOrderByPageIndexAsc("cware_ready"))
                .thenReturn(List.of(new LectureScript()));

        service.processScriptCallback(successRequest("cware_ready"));

        verify(coursewarePageRepository, never()).deleteByCoursewareId(any());
        verify(lectureScriptRepository, never()).deleteByCoursewareId(any());
        verify(lectureScriptRepository, never()).save(any());
        verify(coursewareRepository, never()).save(any(Courseware.class));
        verifyNoInteractions(ttsService);
    }

    @Test
    @DisplayName("TTS 失败不会导致回调事务失败，audioUrl 可降级为 null")
    void processScriptCallback_ttsFailure_doesNotAbortPersistence() {
        Courseware courseware = new Courseware();
        courseware.setId("cware_generating");
        courseware.setStatus(CoursewareStatus.GENERATING_SCRIPT.name());

        when(coursewareRepository.findById("cware_generating")).thenReturn(Optional.of(courseware));
        when(ttsService.synthesizeToAudioUrl(any())).thenThrow(new IllegalStateException("tts down"));

        service.processScriptCallback(successRequest("cware_generating"));

        ArgumentCaptor<LectureScript> scriptCaptor = ArgumentCaptor.forClass(LectureScript.class);
        verify(lectureScriptRepository).save(scriptCaptor.capture());
        assertThat(scriptCaptor.getValue().getAudioUrl()).isNull();
        assertThat(scriptCaptor.getValue().getContent()).isEqualTo("第一页讲稿内容");

        verify(coursewarePageRepository).deleteByCoursewareId("cware_generating");
        verify(lectureScriptRepository).deleteByCoursewareId("cware_generating");
        verify(coursewareRepository).save(eq(courseware));
        assertThat(courseware.getStatus()).isEqualTo(CoursewareStatus.READY.name());
    }

    private ScriptCallbackRequest successRequest(String coursewareId) {
        ScriptCallbackRequest.ScriptNodeDto node = new ScriptCallbackRequest.ScriptNodeDto();
        node.setNodeId("node_001");
        node.setContent("第一页讲稿内容");

        ScriptCallbackRequest.PageScriptDto page = new ScriptCallbackRequest.PageScriptDto();
        page.setPageIndex(1);
        page.setOriginalText("第一页原文");
        page.setScripts(List.of(node));

        ScriptCallbackRequest request = new ScriptCallbackRequest();
        request.setCoursewareId(coursewareId);
        request.setProcessStatus("SUCCESS");
        request.setPages(List.of(page));
        return request;
    }
}
