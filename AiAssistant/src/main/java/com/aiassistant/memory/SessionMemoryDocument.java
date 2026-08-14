package com.aiassistant.memory;

import com.aiassistant.llm.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document("sessionMemories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionMemoryDocument {
    @Id private ObjectId id;
    private Long sessionId;
    private String workorderUserId;
    private String summary;
    private int summaryVersion;
    private long summaryThroughSequence;
    @Builder.Default private List<StoredMessage> recentMessages = new ArrayList<>();
    private long nextSequence;
    private long version;
    private String state;
    private String lastRequestId;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;
    private Instant lastCompressedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StoredMessage {
        private long sequence;
        private ChatMessage message;
    }
}
