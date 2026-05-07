package com.interactive.edu.vo.courseware;

public record CoursewareDetailView(
        String coursewareId,
        String name,
        String status,
        String currentTaskStatus,
        String fileType,
        String createdAt,
        String updatedAt
) {
}
