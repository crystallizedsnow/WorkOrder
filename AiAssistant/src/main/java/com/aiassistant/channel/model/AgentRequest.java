package com.aiassistant.channel.model;

public record AgentRequest(Long sessionId, String userId, String query, String accessToken,
                           ChannelType channel, String tenantId, String senderId, String sourceConversationId, String traceId) {
    public AgentRequest {
        if (sessionId == null || query == null || query.isBlank() || accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("sessionId, query and accessToken are required");
        }
    }
}
