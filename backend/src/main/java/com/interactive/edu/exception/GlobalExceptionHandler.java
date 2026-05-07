package com.interactive.edu.exception;

import com.interactive.edu.dto.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.NoSuchElementException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<Void> handleBusinessException(BusinessException ex) {
        log.warn("Business exception: code={}, message={}", ex.getErrorCode().getCode(), ex.getFriendlyMessage());
        return BaseResponse.error(ex.getErrorCode().getCode(), resolveMessage(ex.getFriendlyMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(ServiceException.class)
    public BaseResponse<Void> handleServiceException(ServiceException ex) {
        log.error("Service exception: code={}, message={}", ex.getErrorCode().getCode(), ex.getFriendlyMessage(), ex);
        return BaseResponse.error(ex.getErrorCode().getCode(), resolveMessage(ex.getFriendlyMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public BaseResponse<Void> handleBadRequest(IllegalArgumentException ex) {
        return BaseResponse.error(
                ErrorCode.PARAM_ERROR.getCode(),
                resolveMessage(ex.getMessage(), ErrorCode.PARAM_ERROR)
        );
    }

    @ExceptionHandler(NoSuchElementException.class)
    public BaseResponse<Void> handleNotFound(NoSuchElementException ex) {
        return BaseResponse.error(
                ErrorCode.NOT_FOUND.getCode(),
                resolveMessage(ex.getMessage(), ErrorCode.NOT_FOUND)
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public BaseResponse<Void> handleConflict(IllegalStateException ex) {
        return BaseResponse.error(
                ErrorCode.STATE_CONFLICT.getCode(),
                resolveMessage(ex.getMessage(), ErrorCode.STATE_CONFLICT)
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        FieldError firstError = ex.getBindingResult().getFieldError();
        String message = firstError == null
                ? ErrorCode.PARAM_ERROR.getDefaultMessage()
                : firstError.getField() + ": " + firstError.getDefaultMessage();
        return BaseResponse.error(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public BaseResponse<Void> handleMissingRequestParameter(MissingServletRequestParameterException ex) {
        return BaseResponse.error(
                ErrorCode.PARAM_ERROR.getCode(),
                "缺少必填参数: " + ex.getParameterName()
        );
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public BaseResponse<Void> handleMissingRequestPart(MissingServletRequestPartException ex) {
        return BaseResponse.error(
                ErrorCode.PARAM_ERROR.getCode(),
                "缺少必填参数: " + ex.getRequestPartName()
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public BaseResponse<Void> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return BaseResponse.error(ErrorCode.PARAM_ERROR.getCode(), "上传文件超过大小限制");
    }

    @ExceptionHandler(Exception.class)
    public BaseResponse<Void> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return BaseResponse.error(ErrorCode.INTERNAL_ERROR.getCode(), "服务内部错误，请稍后重试");
    }

    private String resolveMessage(String message, ErrorCode errorCode) {
        return StringUtils.hasText(message) ? message : errorCode.getDefaultMessage();
    }
}
