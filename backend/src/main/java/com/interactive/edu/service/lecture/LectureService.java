package com.interactive.edu.service.lecture;

import com.interactive.edu.service.courseware.CoursewareService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class LectureService {

    private final CoursewareService coursewareService;
    private final ConcurrentMap<String, SessionState> sessionStore = new ConcurrentHashMap<>();

    public LectureSessionView startLecture(String coursewareId, String userId) {
        if (!StringUtils.hasText(coursewareId)) {
            throw new IllegalArgumentException("coursewareId 不能为空");
        }
        coursewareService.requireScript(coursewareId);

        String sessionId = "sess_" + UUID.randomUUID().toString().replace("-", "");
        String resolvedUserId = StringUtils.hasText(userId) ? userId.trim() : "demo_user";
        SessionState state = new SessionState(sessionId, coursewareId, resolvedUserId);
        sessionStore.put(sessionId, state);

        return new LectureSessionView(
                sessionId,
                "PLAYING",
                coursewareService.getCurrentNode(coursewareId, state.getCurrentPageIndex())
        );
    }

    public SessionStatusView pause(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        SessionState session = requireSession(sessionId);
        session.setStatus("PAUSED");
        session.touch();
        return new SessionStatusView(sessionId, session.getStatus());
    }

    public LectureSessionView resume(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        SessionState session = requireSession(sessionId);
        session.setStatus("PLAYING");
        session.touch();
        return new LectureSessionView(
                session.getSessionId(),
                session.getStatus(),
                coursewareService.getCurrentNode(session.getCoursewareId(), session.getCurrentPageIndex())
        );
    }

    public SessionSnapshot getSessionSnapshot(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        SessionState session = requireSession(sessionId);
        return new SessionSnapshot(
                session.getSessionId(),
                session.getCoursewareId(),
                session.getUserId(),
                session.getCurrentPageIndex(),
                session.getStatus()
        );
    }

    private SessionState requireSession(String sessionId) {
        SessionState session = sessionStore.get(sessionId);
        if (session == null) {
            throw new NoSuchElementException("讲课会话不存在");
        }
        return session;
    }

    public record LectureSessionView(
            String sessionId,
            String status,
            CoursewareService.CurrentNodeView currentNode
    ) {
    }

    public record SessionStatusView(String sessionId, String status) {
    }

    public record SessionSnapshot(
            String sessionId,
            String coursewareId,
            String userId,
            int currentPageIndex,
            String status
    ) {
    }

    @Getter
    private static final class SessionState {
        private final String sessionId;
        private final String coursewareId;
        private final String userId;
        private final Instant createdAt = Instant.now();
        private volatile Instant updatedAt = createdAt;
        private volatile int currentPageIndex = 1;
        private volatile String status = "PLAYING";

        private SessionState(String sessionId, String coursewareId, String userId) {
            this.sessionId = sessionId;
            this.coursewareId = coursewareId;
            this.userId = userId;
        }

        private void setStatus(String status) {
            this.status = status;
        }

        private void touch() {
            this.updatedAt = Instant.now();
        }
    }
}
