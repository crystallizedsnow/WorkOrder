package com.aiassistant.memory;

import java.time.Instant;
import java.util.Optional;

public interface SessionMemoryRepository {
    Optional<SessionMemoryDocument> find(Long sessionId);
    SessionMemoryDocument create(Long sessionId, String workorderUserId, Instant expiresAt);
    SessionMemoryDocument save(SessionMemoryDocument document, long expectedVersion);
    void delete(Long sessionId, String workorderUserId);
}
