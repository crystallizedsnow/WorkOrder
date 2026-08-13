package com.aiassistant.channel.model;

public record OutboundMessage(String conversationId, String replyToMessageId, String text, String traceId) {
    public OutboundMessage {
        if (conversationId == null || conversationId.isBlank() || text == null) {
            throw new IllegalArgumentException("conversationId and text are required");
        }
    }
}
