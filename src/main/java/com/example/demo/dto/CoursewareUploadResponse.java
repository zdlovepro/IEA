package com.example.demo.dto;

public class CoursewareUploadResponse {
    private String code;
    private String message;
    private Data data;

    public CoursewareUploadResponse(String code, String message, Data data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static class Data {
        private String coursewareId;
        private String status;

        public Data(String coursewareId, String status) {
            this.coursewareId = coursewareId;
            this.status = status;
        }
    }
}
