package com.example.workorder.cli.guard;

public class WriteGuardException extends RuntimeException {
    private final WriteGuardErrorCode errorCode;
    public WriteGuardException(WriteGuardErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    public WriteGuardErrorCode getErrorCode() { return errorCode; }
}
