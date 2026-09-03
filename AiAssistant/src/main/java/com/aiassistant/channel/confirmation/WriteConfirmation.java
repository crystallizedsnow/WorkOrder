package com.aiassistant.channel.confirmation;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document("channel_write_confirmations")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class WriteConfirmation {
    @Id private String operationId;
    @Indexed private Long sessionId;
    private String userId;
    private String tenantId;
    private String conversationId;
    private String requesterOpenId;
    private String command;
    private String previewId;
    private String commandDigest;
    private String summary;
    private Status status;
    @Indexed(expireAfter = "0s") private Instant purgeAt;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant decidedAt;
    private String resultSummary;
    private String traceId;
    public enum Status { PENDING, EXECUTING, SUCCEEDED, FAILED, UNKNOWN, CANCELLED, EXPIRED }
}
