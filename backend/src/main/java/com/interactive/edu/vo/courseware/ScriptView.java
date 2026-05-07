package com.interactive.edu.vo.courseware;

import java.util.List;

public record ScriptView(
        String coursewareId,
        List<OutlineItemView> outline,
        List<ScriptSegmentView> segments,
        String status
) {
}
