package com.interactive.edu.exception;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String friendlyMessage;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String friendlyMessage) {
        super(friendlyMessage);
        this.errorCode = errorCode;
        this.friendlyMessage = friendlyMessage;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getFriendlyMessage() {
        return friendlyMessage;
    }
}
