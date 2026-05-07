package com.interactive.edu.exception;

/**
 * TTS 语音合成异常。
 * <p>
 * 覆盖以下场景：
 * <ul>
 *   <li>鉴权 Token 获取失败（网络不通、Key 错误）→ {@link ErrorCode#TTS_TOKEN_FETCH_FAILED}</li>
 *   <li>TTS 合成调用失败（服务端 4xx/5xx）→ {@link ErrorCode#TTS_SYNTHESIS_FAILED}</li>
 *   <li>请求参数非法（文本为空、超长）→ {@link ErrorCode#TTS_INVALID_REQUEST}</li>
 * </ul>
 */
public class TtsException extends RuntimeException {

    private final ErrorCode errorCode;

    public TtsException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public TtsException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public int getCode() {
        return errorCode.getCode();
    }
}
