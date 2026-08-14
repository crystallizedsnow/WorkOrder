package com.aiassistant.store;

import com.aiassistant.memory.MemoryVersionConflictException;
import com.aiassistant.memory.SessionMemoryDocument;
import com.aiassistant.memory.SessionMemoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MongoSessionMemoryRepository implements SessionMemoryRepository {
    private final MongoTemplate mongo;

    @Override
    public Optional<SessionMemoryDocument> find(Long sessionId) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("sessionId").is(sessionId)), SessionMemoryDocument.class));
    }

    @Override
    public SessionMemoryDocument create(Long sessionId, String userId, Instant expiresAt) {
        Instant now = Instant.now();
        SessionMemoryDocument value = SessionMemoryDocument.builder().sessionId(sessionId).workorderUserId(userId)
                .summaryVersion(1).nextSequence(1).version(0).state("ACTIVE")
                .createdAt(now).updatedAt(now).expiresAt(expiresAt).build();
        try {
            return mongo.insert(value);
        } catch (DuplicateKeyException duplicate) {
            return find(sessionId).orElseThrow(() -> duplicate);
        }
    }

    @Override
    public SessionMemoryDocument save(SessionMemoryDocument value, long expectedVersion) {
        Query query = Query.query(Criteria.where("sessionId").is(value.getSessionId())
                .and("version").is(expectedVersion));
        if (value.getWorkorderUserId() != null) query.addCriteria(Criteria.where("workorderUserId").is(value.getWorkorderUserId()));
        Update update = new Update().set("summary", value.getSummary())
                .set("summaryVersion", value.getSummaryVersion())
                .set("summaryThroughSequence", value.getSummaryThroughSequence())
                .set("recentMessages", value.getRecentMessages()).set("nextSequence", value.getNextSequence())
                .set("state", value.getState()).set("lastRequestId", value.getLastRequestId())
                .set("updatedAt", value.getUpdatedAt()).set("expiresAt", value.getExpiresAt())
                .set("lastCompressedAt", value.getLastCompressedAt()).inc("version", 1);
        SessionMemoryDocument saved = mongo.findAndModify(query, update,
                org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true), SessionMemoryDocument.class);
        if (saved == null) throw new MemoryVersionConflictException(value.getSessionId());
        return saved;
    }

    @Override
    public void delete(Long sessionId, String userId) {
        Criteria criteria = Criteria.where("sessionId").is(sessionId);
        if (userId != null) criteria.and("workorderUserId").is(userId);
        mongo.remove(Query.query(criteria), SessionMemoryDocument.class);
    }
}
