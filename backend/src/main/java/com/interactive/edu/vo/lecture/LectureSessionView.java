package com.interactive.edu.vo.lecture;

import com.interactive.edu.vo.courseware.CurrentNodeView;

public record LectureSessionView(
        String sessionId,
        String status,
        CurrentNodeView currentNode
) {
}
