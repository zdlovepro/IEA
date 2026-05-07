package com.interactive.edu.vo.courseware;

public record CoursewareListItem(
        String id,
        String name,
        String status,
        String createdAt,
        String currentTaskStatus
) {
}
