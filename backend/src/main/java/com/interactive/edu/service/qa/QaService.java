package com.interactive.edu.service.qa;

import com.interactive.edu.service.courseware.CoursewareService;
import com.interactive.edu.service.lecture.LectureService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QaService {

    private final LectureService lectureService;
    private final CoursewareService coursewareService;

    public QaAnswerView askText(String sessionId, String question) {
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("question 不能为空");
        }

        long startAt = System.currentTimeMillis();
        LectureService.SessionSnapshot session = lectureService.getSessionSnapshot(sessionId);
        List<CoursewareService.ScriptSegment> segments = coursewareService.getScriptSegments(session.coursewareId());
        CoursewareService.ScriptSegment current = coursewareService.getSegmentForPage(
                session.coursewareId(),
                session.currentPageIndex()
        );
        CoursewareService.ScriptSegment target = selectTargetSegment(segments, current, question);

        String answer = buildAnswer(question, target);
        List<EvidenceItem> evidence = buildEvidence(current, target);
        long latencyMs = Math.max(1, System.currentTimeMillis() - startAt);

        return new QaAnswerView(answer, evidence, latencyMs);
    }

    private CoursewareService.ScriptSegment selectTargetSegment(
            List<CoursewareService.ScriptSegment> segments,
            CoursewareService.ScriptSegment current,
            String question
    ) {
        for (CoursewareService.ScriptSegment segment : segments) {
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

    private String buildAnswer(String question, CoursewareService.ScriptSegment target) {
        StringBuilder builder = new StringBuilder();
        builder.append("结合当前课件内容，");

        if (question.contains("什么是") || question.contains("是什么")) {
            builder.append(target.title()).append("可以理解为：");
        } else {
            builder.append("这个问题可以先从第 ")
                    .append(target.pageIndex())
                    .append(" 页的内容来回答：");
        }

        builder.append(target.content());

        if (!target.knowledgePoints().isEmpty()) {
            builder.append(" 建议你重点关注：")
                    .append(String.join("、", target.knowledgePoints()))
                    .append("。");
        }

        return builder.toString();
    }

    private List<EvidenceItem> buildEvidence(
            CoursewareService.ScriptSegment current,
            CoursewareService.ScriptSegment target
    ) {
        List<EvidenceItem> evidence = new ArrayList<>();
        evidence.add(new EvidenceItem("page_" + target.pageIndex(), summarize(target.content())));

        if (!target.id().equals(current.id())) {
            evidence.add(new EvidenceItem("page_" + current.pageIndex(), summarize(current.content())));
        }

        return evidence;
    }

    private String summarize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.length() <= 120 ? text : text.substring(0, 120) + "...";
    }

    public record QaAnswerView(String answer, List<EvidenceItem> evidence, long latencyMs) {
    }

    public record EvidenceItem(String source, String text) {
    }
}
