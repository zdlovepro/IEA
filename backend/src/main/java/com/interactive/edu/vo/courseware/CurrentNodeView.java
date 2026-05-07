package com.interactive.edu.vo.courseware;

public record CurrentNodeView(
        String nodeId,
        int pageIndex,
        String content,
        String audioUrl
) {
}
