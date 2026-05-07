package com.interactive.edu.dto;

public class ApiResponse<T> extends BaseResponse<T> {

    public ApiResponse(int code, String message, T data) {
        super(code, message, data);
    }

    public static <T> ApiResponse<T> ok(T data) {
        BaseResponse<T> response = BaseResponse.ok(data);
        return new ApiResponse<>(response.getCode(), response.getMessage(), response.getData());
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        BaseResponse<T> response = BaseResponse.error(code, message);
        return new ApiResponse<>(response.getCode(), response.getMessage(), response.getData());
    }
}