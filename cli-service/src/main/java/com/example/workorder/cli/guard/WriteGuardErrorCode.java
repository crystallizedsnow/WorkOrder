package com.example.workorder.cli.guard;

public enum WriteGuardErrorCode {
    PREVIEW_REQUIRED(46001), PREVIEW_NOT_FOUND(46002), PREVIEW_EXPIRED(46003),
    PREVIEW_NOT_CONFIRMED(46004), PREVIEW_MISMATCH(46005), PREVIEW_ALREADY_CONSUMED(46006),
    PREVIEW_CANCELLED(46007), SCHEMA_CHANGED(46008), PREVIEW_FORBIDDEN(46009), PREVIEW_INVALID(46010);
    private final int code;
    WriteGuardErrorCode(int code) { this.code = code; }
    public int code() { return code; }
}
