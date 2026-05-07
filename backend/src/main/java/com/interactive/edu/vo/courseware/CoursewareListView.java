package com.interactive.edu.vo.courseware;

import java.util.List;

public record CoursewareListView(
        List<CoursewareListItem> items,
        int total,
        int page,
        int pageSize
) {
}
