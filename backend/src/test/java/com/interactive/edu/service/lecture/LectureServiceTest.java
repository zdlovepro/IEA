package com.interactive.edu.service.lecture;

import com.interactive.edu.service.courseware.CoursewareService;
import com.interactive.edu.vo.courseware.CurrentNodeView;
import com.interactive.edu.vo.lecture.LectureSessionView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LectureServiceTest {

    @Mock
    private CoursewareService coursewareService;

    private LectureService lectureService;

    @BeforeEach
    void setUp() {
        lectureService = new LectureService(coursewareService);
    }

    @Test
    @DisplayName("heartbeat updates last seen timestamp")
    void updateHeartbeat_updatesLastSeenAt() {
        stubCurrentNode(1, "node_1", "Page 1 content");
        LectureSessionView session = lectureService.startLecture("cware_ws", "user_ws");

        LectureService.LectureRealtimeState before = lectureService.getRealtimeState(session.sessionId());
        LectureService.LectureRealtimeState after = lectureService.updateHeartbeat(session.sessionId());

        assertThat(after.lastSeenAt()).isAfterOrEqualTo(before.lastSeenAt());
        assertThat(after.status()).isEqualTo("PLAYING");
    }

    @Test
    @DisplayName("interrupt stores page index and breakpoint time")
    void interrupt_storesBreakpoint() {
        stubCurrentNode(1, "node_1", "Page 1 content");
        stubCurrentNode(2, "node_2", "Page 2 content");
        LectureSessionView session = lectureService.startLecture("cware_ws", "user_ws");

        LectureService.LectureRealtimeState interrupted = lectureService.interrupt(session.sessionId(), 2, 12.3);

        assertThat(interrupted.status()).isEqualTo("INTERRUPTED");
        assertThat(interrupted.currentPageIndex()).isEqualTo(2);
        assertThat(interrupted.breakpointTime()).isEqualTo(12.3);
        assertThat(interrupted.currentNode().pageIndex()).isEqualTo(2);
    }

    @Test
    @DisplayName("resume returns saved breakpoint and current node")
    void resumeFromBreakpoint_returnsStoredPosition() {
        stubCurrentNode(1, "node_1", "Page 1 content");
        stubCurrentNode(2, "node_2", "Page 2 content");
        LectureSessionView session = lectureService.startLecture("cware_ws", "user_ws");
        lectureService.interrupt(session.sessionId(), 2, 18.6);

        LectureService.ResumeState resumed = lectureService.resumeFromBreakpoint(session.sessionId());
        LectureService.LectureRealtimeState currentState = lectureService.getRealtimeState(session.sessionId());

        assertThat(resumed.currentPageIndex()).isEqualTo(2);
        assertThat(resumed.breakpointTime()).isEqualTo(18.6);
        assertThat(resumed.currentNode().nodeId()).isEqualTo("node_2");
        assertThat(currentState.status()).isEqualTo("PLAYING");
    }

    @Test
    @DisplayName("missing session throws not found")
    void getRealtimeState_missingSession_throws() {
        assertThatThrownBy(() -> lectureService.getRealtimeState("missing"))
                .isInstanceOf(NoSuchElementException.class);
    }

    private void stubCurrentNode(int pageIndex, String nodeId, String content) {
        when(coursewareService.getCurrentNode(eq("cware_ws"), eq(pageIndex)))
                .thenReturn(new CurrentNodeView(nodeId, pageIndex, content, null));
    }
}
