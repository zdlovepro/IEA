package com.interactive.edu.controller;

import com.interactive.edu.dto.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public BaseResponse<Void> handleBadRequest(IllegalArgumentException ex) {
        return BaseResponse.error(40001, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public BaseResponse<Void> handleConflict(IllegalStateException ex) {
        return BaseResponse.error(40901, ex.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public BaseResponse<Void> handleNotFound(NoSuchElementException ex) {
        return BaseResponse.error(40401, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        return BaseResponse.error(40001, "请求参数校验失败");
    }

    @ExceptionHandler(Exception.class)
    public BaseResponse<Void> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return BaseResponse.error(50001, "服务内部错误");
    }
}
