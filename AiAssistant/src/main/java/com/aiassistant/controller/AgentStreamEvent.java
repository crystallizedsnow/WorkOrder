package com.aiassistant.controller;

import java.util.Map;

public record AgentStreamEvent(String requestId, String content, String code, boolean retryable,
                               Map<String, Object> metadata) {
    public static AgentStreamEvent content(String requestId, String content) {
        return new AgentStreamEvent(requestId, content, null, false, Map.of());
    }

    public static AgentStreamEvent status(String requestId, String stage) {
        return new AgentStreamEvent(requestId, "", null, false, Map.of("stage", stage));
    }

    public static AgentStreamEvent error(String requestId, Throwable error) {
        return new AgentStreamEvent(requestId, "Agent 执行失败，请稍后重试", "AGENT_EXECUTION_FAILED", true,
                Map.of("errorType", error.getClass().getSimpleName()));
    }
}
