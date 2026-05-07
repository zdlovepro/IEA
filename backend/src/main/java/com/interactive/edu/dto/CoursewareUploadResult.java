package com.interactive.edu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CoursewareUploadResult {
    private String coursewareId;
    private String status; // UPLOADED
}