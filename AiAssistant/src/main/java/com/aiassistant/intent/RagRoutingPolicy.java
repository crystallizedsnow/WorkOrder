package com.aiassistant.intent;

import org.springframework.stereotype.Component;

@Component
public class RagRoutingPolicy {
    public RagMode decide(RoutingDecision decision) {
        if (decision == null) return RagMode.NEVER;
        return switch (decision.routeType()) {
            case KNOWLEDGE_QA -> RagMode.REQUIRED;
            case SKILL_EXECUTION, SYSTEM_HELP, CONTROL_CONTINUATION, CLARIFY, OUT_OF_SCOPE -> RagMode.NEVER;
        };
    }
}
