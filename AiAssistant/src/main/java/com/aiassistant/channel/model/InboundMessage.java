package com.aiassistant.channel.model;

import java.time.Instant;
import java.util.Map;

public record InboundMessage(
        ChannelType channel,
        String botAccountId,
        String tenantId,
        String conversationId,
        String messageId,
        String senderId,
        String senderUnionId,
        String text,
        String messageType,
        String traceId,
        Instant receivedAt,
        Map<String, String> attributes) {

    public InboundMessage {
        if (channel == null || isBlank(messageId) || isBlank(conversationId) || isBlank(senderId)) {
            throw new IllegalArgumentException("channel, messageId, conversationId and senderId are required");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        receivedAt = receivedAt == null ? Instant.now() : receivedAt;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
