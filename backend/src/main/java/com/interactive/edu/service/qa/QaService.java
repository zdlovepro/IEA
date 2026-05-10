package com.interactive.edu.service.qa;

import com.interactive.edu.service.courseware.CoursewareService;
import com.interactive.edu.service.lecture.LectureService;
import com.interactive.edu.service.python.PythonQaClient;
import com.interactive.edu.service.python.PythonQaRequest;
import com.interactive.edu.service.python.PythonQaResponse;
import com.interactive.edu.vo.courseware.ScriptSegmentView;
import com.interactive.edu.vo.qa.EvidenceItemView;
import com.interactive.edu.vo.qa.QaAnswerView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class QaService {

    private final LectureService lectureService;
    private final CoursewareService coursewareService;
    private final PythonQaClient pythonQaClient;

    public QaAnswerView askText(String sessionId, String question) {
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("question 不能为空");
        }

        long startAt = System.currentTimeMillis();
        LectureService.SessionSnapshot session = lectureService.getSessionSnapshot(sessionId);
        PythonQaResponse pythonQaResponse = askPythonFirst(session, question);
        if (pythonQaResponse != null) {
            long latencyMs = resolveLatencyMs(startAt, pythonQaResponse.latencyMs());
            log.info(
                    "QA answered by Python RAG. sessionId={}, coursewareId={}, pageIndex={}, latencyMs={}",
                    sessionId,
                    session.coursewareId(),
                    session.currentPageIndex(),
                    latencyMs
            );
            return new QaAnswerView(
                    pythonQaResponse.answer(),
                    pythonQaResponse.safeEvidence().stream()
                            .map(this::toEvidenceItemView)
                            .toList(),
                    latencyMs
            );
        }

        List<ScriptSegmentView> segments = coursewareService.getScriptSegments(session.coursewareId());
        ScriptSegmentView current = coursewareService.getSegmentForPage(session.coursewareId(), session.currentPageIndex());
        ScriptSegmentView target = selectTargetSegment(segments, current, question);

        String answer = buildAnswer(question, target);
        List<EvidenceItemView> evidence = buildEvidence(current, target);
        long latencyMs = Math.max(1, System.currentTimeMillis() - startAt);
        log.info(
                "QA answered by local fallback. sessionId={}, coursewareId={}, pageIndex={}, latencyMs={}",
                sessionId,
                session.coursewareId(),
                session.currentPageIndex(),
                latencyMs
        );

        return new QaAnswerView(answer, evidence, latencyMs);
    }

    private PythonQaResponse askPythonFirst(LectureService.SessionSnapshot session, String question) {
        try {
            return pythonQaClient.askText(new PythonQaRequest(
                    session.sessionId(),
                    session.coursewareId(),
                    session.currentPageIndex(),
                    question.trim(),
                    5
            ));
        } catch (Exception ex) {
            log.warn(
                    "Python QA unavailable, fallback to local template. sessionId={}, coursewareId={}, pageIndex={}, reason={}",
                    session.sessionId(),
                    session.coursewareId(),
                    session.currentPageIndex(),
                    ex.getMessage()
            );
            return null;
        }
    }

    private ScriptSegmentView selectTargetSegment(
            List<ScriptSegmentView> segments,
            ScriptSegmentView current,
            String question
    ) {
        for (ScriptSegmentView segment : segments) {
            if (question.contains(segment.title())) {
                return segment;
            }

            for (String knowledgePoint : segment.knowledgePoints()) {
                if (question.contains(knowledgePoint)) {
                    return segment;
                }
            }
        }

        return current;
    }

    private String buildAnswer(String question, ScriptSegmentView target) {
        StringBuilder builder = new StringBuilder();
        builder.append("结合当前课件内容，");

        if (question.contains("什么是") || question.contains("是什么")) {
            builder.append(target.title()).append("可以理解为：");
        } else {
            builder.append("这个问题可以先从第")
                    .append(target.pageIndex())
                    .append("页的内容来回答：");
        }

        builder.append(target.content());

        if (!target.knowledgePoints().isEmpty()) {
            builder.append(" 建议你重点关注：")
                    .append(String.join("、", target.knowledgePoints()))
                    .append("。");
        }

        return builder.toString();
    }

    private List<EvidenceItemView> buildEvidence(
            ScriptSegmentView current,
            ScriptSegmentView target
    ) {
        List<EvidenceItemView> evidence = new ArrayList<>();
        evidence.add(new EvidenceItemView(
                "page_" + target.pageIndex(),
                summarize(target.content()),
                target.pageIndex(),
                target.nodeId()
        ));

        if (!target.id().equals(current.id())) {
            evidence.add(new EvidenceItemView(
                    "page_" + current.pageIndex(),
                    summarize(current.content()),
                    current.pageIndex(),
                    current.nodeId()
            ));
        }

        return evidence;
    }

    private EvidenceItemView toEvidenceItemView(PythonQaResponse.EvidencePayload evidencePayload) {
        return new EvidenceItemView(
                evidencePayload.source(),
                evidencePayload.text(),
                evidencePayload.pageIndex(),
                evidencePayload.chunkId()
        );
    }

    private String summarize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.length() <= 120 ? text : text.substring(0, 120) + "...";
    }

    private long resolveLatencyMs(long startAt, long pythonLatencyMs) {
        long measuredLatency = Math.max(1, System.currentTimeMillis() - startAt);
        return pythonLatencyMs > 0 ? pythonLatencyMs : measuredLatency;
    }
}
