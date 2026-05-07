package com.interactive.edu.vo.courseware;

import java.util.List;

public record ScriptSegmentView(
        String id,
        String nodeId,
        int pageIndex,
        String title,
        String content,
        List<String> knowledgePoints,
        String audioUrl
) {
}
