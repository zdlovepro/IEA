package com.interactive.edu.service.lecture;

import com.interactive.edu.enums.LectureSessionStatus;
import com.interactive.edu.service.courseware.CoursewareService;
import com.interactive.edu.vo.courseware.CurrentNodeView;
import com.interactive.edu.vo.lecture.LectureSessionView;
import com.interactive.edu.vo.lecture.SessionStatusView;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class LectureService {

    private final CoursewareService coursewareService;
    private final ConcurrentMap<String, SessionState> sessionStore = new ConcurrentHashMap<>();

    public LectureSessionView startLecture(String coursewareId, String userId) {
        if (!StringUtils.hasText(coursewareId)) {
            throw new IllegalArgumentException("coursewareId must not be blank");
        }

        coursewareService.requireScript(coursewareId);
        String sessionId = "sess_" + UUID.randomUUID().toString().replace("-", "");
        String resolvedUserId = StringUtils.hasText(userId) ? userId.trim() : "demo_user";

        SessionState state = new SessionState(sessionId, coursewareId, resolvedUserId);
        state.markPlaying();
        sessionStore.put(sessionId, state);
        log.info("Lecture started. sessionId={}, coursewareId={}, userId={}", sessionId, coursewareId, resolvedUserId);

        return new LectureSessionView(
                sessionId,
                state.getStatus(),
                coursewareService.getCurrentNode(coursewareId, state.getCurrentPageIndex()),
                state.getCurrentPageIndex(),
                state.getBreakpointTime()
        );
    }

    public SessionStatusView pause(String sessionId) {
        LectureRealtimeState state = interrupt(sessionId, null, null);
        return new SessionStatusView(state.sessionId(), state.status());
    }

    public LectureSessionView resume(String sessionId) {
        ResumeState resumeState = resumeFromBreakpoint(sessionId);
        return new LectureSessionView(
                resumeState.sessionId(),
                LectureSessionStatus.PLAYING.name(),
                resumeState.currentNode(),
                resumeState.currentPageIndex(),
                resumeState.breakpointTime()
        );
    }

    public SessionSnapshot getSessionSnapshot(String sessionId) {
        validateSessionId(sessionId);

        SessionState session = requireSession(sessionId);
        return new SessionSnapshot(
                session.getSessionId(),
                session.getCoursewareId(),
                session.getUserId(),
                session.getCurrentPageIndex(),
                session.getStatus()
        );
    }

    public LectureRealtimeState interrupt(String sessionId, Integer pageIndex, Double currentTime) {
        validateSessionId(sessionId);

        LectureRealtimeState state = updateBreakpoint(sessionId, pageIndex, currentTime);
        SessionState session = requireSession(sessionId);
        session.markInterrupted();
        log.info(
                "Lecture interrupted. sessionId={}, coursewareId={}, pageIndex={}, currentTime={}",
                sessionId,
                session.getCoursewareId(),
                state.currentPageIndex(),
                state.breakpointTime()
        );
        return toRealtimeState(session);
    }

    public LectureRealtimeState updateBreakpoint(String sessionId, Integer pageIndex, Double currentTime) {
        validateSessionId(sessionId);

        SessionState session = requireSession(sessionId);
        int resolvedPageIndex = resolvePageIndex(pageIndex, session.getCurrentPageIndex());
        double resolvedCurrentTime = resolveCurrentTime(currentTime, session.getBreakpointTime());
        session.updateBreakpoint(resolvedPageIndex, resolvedCurrentTime);
        log.info(
                "Lecture breakpoint updated. sessionId={}, coursewareId={}, pageIndex={}, currentTime={}",
                sessionId,
                session.getCoursewareId(),
                resolvedPageIndex,
                resolvedCurrentTime
        );
        return toRealtimeState(session);
    }

    public ResumeState resumeFromBreakpoint(String sessionId) {
        validateSessionId(sessionId);

        SessionState session = requireSession(sessionId);
        session.markResuming();
        CurrentNodeView currentNode = coursewareService.getCurrentNode(
                session.getCoursewareId(),
                session.getCurrentPageIndex()
        );
        double breakpointTime = session.getBreakpointTime();
        int currentPageIndex = session.getCurrentPageIndex();
        session.markPlaying();
        log.info(
                "Lecture resumed from breakpoint. sessionId={}, coursewareId={}, pageIndex={}, breakpointTime={}",
                sessionId,
                session.getCoursewareId(),
                currentPageIndex,
                breakpointTime
        );
        return new ResumeState(session.getSessionId(), currentPageIndex, breakpointTime, currentNode);
    }

    public LectureRealtimeState updateHeartbeat(String sessionId) {
        validateSessionId(sessionId);

        SessionState session = requireSession(sessionId);
        session.updateHeartbeat();
        log.debug("Lecture heartbeat updated. sessionId={}, coursewareId={}", sessionId, session.getCoursewareId());
        return toRealtimeState(session);
    }

    public LectureRealtimeState getRealtimeState(String sessionId) {
        validateSessionId(sessionId);
        return toRealtimeState(requireSession(sessionId));
    }

    private void validateSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
    }

    private int resolvePageIndex(Integer requestedPageIndex, int currentPageIndex) {
        if (requestedPageIndex == null) {
            return currentPageIndex;
        }
        if (requestedPageIndex <= 0) {
            throw new IllegalArgumentException("pageIndex must be positive");
        }
        return requestedPageIndex;
    }

    private double resolveCurrentTime(Double requestedCurrentTime, double currentTime) {
        if (requestedCurrentTime == null) {
            return currentTime;
        }
        if (requestedCurrentTime < 0) {
            throw new IllegalArgumentException("currentTime must be greater than or equal to 0");
        }
        return requestedCurrentTime;
    }

    private LectureRealtimeState toRealtimeState(SessionState session) {
        return new LectureRealtimeState(
                session.getSessionId(),
                session.getCoursewareId(),
                session.getStatus(),
                session.getCurrentPageIndex(),
                session.getBreakpointTime(),
                session.getLastSeenAt(),
                coursewareService.getCurrentNode(session.getCoursewareId(), session.getCurrentPageIndex())
        );
    }

    private SessionState requireSession(String sessionId) {
        SessionState session = sessionStore.get(sessionId);
        if (session == null) {
            throw new NoSuchElementException("lecture session not found");
        }
        return session;
    }

    public record SessionSnapshot(
            String sessionId,
            String coursewareId,
            String userId,
            int currentPageIndex,
            String status
    ) {
    }

    public record LectureRealtimeState(
            String sessionId,
            String coursewareId,
            String status,
            int currentPageIndex,
            double breakpointTime,
            Instant lastSeenAt,
            CurrentNodeView currentNode
    ) {
    }

    public record ResumeState(
            String sessionId,
            int currentPageIndex,
            double breakpointTime,
            CurrentNodeView currentNode
    ) {
    }

    @Getter
    private static final class SessionState {
        private final String sessionId;
        private final String coursewareId;
        private final String userId;
        private final Instant createdAt = Instant.now();
        private volatile Instant updatedAt = createdAt;
        private volatile Instant lastSeenAt = createdAt;
        private volatile int currentPageIndex = 1;
        private volatile double breakpointTime = 0D;
        private volatile String status = LectureSessionStatus.IDLE.name();

        private SessionState(String sessionId, String coursewareId, String userId) {
            this.sessionId = sessionId;
            this.coursewareId = coursewareId;
            this.userId = userId;
        }

        private synchronized void updateBreakpoint(int pageIndex, double currentTime) {
            this.currentPageIndex = pageIndex;
            this.breakpointTime = currentTime;
            touch();
        }

        private synchronized void markInterrupted() {
            this.status = LectureSessionStatus.INTERRUPTED.name();
            touch();
        }

        private synchronized void markResuming() {
            this.status = LectureSessionStatus.RESUMING.name();
            touch();
        }

        private synchronized void markPlaying() {
            this.status = LectureSessionStatus.PLAYING.name();
            touch();
        }

        private synchronized void updateHeartbeat() {
            touch();
        }

        private synchronized void touch() {
            Instant now = Instant.now();
            this.updatedAt = now;
            this.lastSeenAt = now;
        }
    }
}
