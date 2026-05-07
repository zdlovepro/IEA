package com.interactive.edu.exception;

public class ServiceException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String friendlyMessage;

    public ServiceException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public ServiceException(ErrorCode errorCode, String friendlyMessage) {
        super(friendlyMessage);
        this.errorCode = errorCode;
        this.friendlyMessage = friendlyMessage;
    }

    public ServiceException(ErrorCode errorCode, String friendlyMessage, Throwable cause) {
        super(friendlyMessage, cause);
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
