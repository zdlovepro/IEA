package com.interactive.edu.exception;

/**
 * 统一业务错误码枚举。
 * <p>
 * 与 {@code docs/api.md} 第 2.5 节通用错误码保持对齐。
 */
public enum ErrorCode {

    // ---- 通用 ----
    PARAM_ERROR(40001, "参数缺失或格式错误"),
    BUSINESS_VALIDATION_FAILED(40002, "业务校验失败"),
    UNAUTHORIZED(40101, "未登录或 Token 无效"),
    FORBIDDEN(40301, "无权限访问"),
    NOT_FOUND(40401, "资源不存在"),
    STATE_CONFLICT(40901, "状态冲突或重复操作"),
    INTERNAL_ERROR(50001, "服务内部错误"),
    PYTHON_SERVICE_ERROR(50201, "下游 Python 服务异常"),

    // ---- 第三方媒体服务 ----
    THIRD_PARTY_MEDIA_ERROR(50202, "第三方模型或媒体服务异常"),

    // ---- TTS 细分 ----
    TTS_TOKEN_FETCH_FAILED(50211, "TTS Token 获取失败"),
    TTS_SYNTHESIS_FAILED(50212, "TTS 合成失败"),
    TTS_INVALID_REQUEST(40011, "TTS 请求参数非法"),
    TTS_AUDIO_UPLOAD_FAILED(50213, "TTS 音频上传至对象存储失败");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
