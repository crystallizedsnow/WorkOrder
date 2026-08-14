package com.aiassistant.store;

import com.aiassistant.memory.SessionMemoryDocument;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class SessionMemoryIndexInitializer {
    private final MongoTemplate mongo;

    @PostConstruct
    void ensureIndexes() {
        var indexes = mongo.indexOps(SessionMemoryDocument.class);
        indexes.ensureIndex(new Index().on("sessionId", Sort.Direction.ASC).unique().named("sessionId"));
        indexes.ensureIndex(new Index().on("workorderUserId", Sort.Direction.ASC)
                .on("updatedAt", Sort.Direction.DESC).named("idx_session_memory_owner_updated"));
        Index ttl = new Index().on("expiresAt", Sort.Direction.ASC).expire(Duration.ZERO).named("expiresAt");
        try {
            indexes.ensureIndex(ttl);
        } catch (DataAccessException conflict) {
            if (conflict.getMessage() == null || !conflict.getMessage().contains("IndexOptionsConflict")) throw conflict;
            indexes.dropIndex("expiresAt");
            indexes.ensureIndex(ttl);
        }
    }
}
