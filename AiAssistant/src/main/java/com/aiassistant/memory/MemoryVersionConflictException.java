package com.aiassistant.memory;

public class MemoryVersionConflictException extends RuntimeException {
    public MemoryVersionConflictException(Long sessionId) {
        super("Session memory version conflict: " + sessionId);
    }
}
