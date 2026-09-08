package com.aiassistant.intent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagRoutingPolicyTest {
    private final RagRoutingPolicy policy = new RagRoutingPolicy();

    @Test
    void onlyKnowledgeQaRequiresRag() {
        for (RouteType type : RouteType.values()) {
            RagMode expected = type == RouteType.KNOWLEDGE_QA ? RagMode.REQUIRED : RagMode.NEVER;
            assertEquals(expected, policy.decide(decision(type)), type.name());
        }
    }

    private RoutingDecision decision(RouteType type) {
        return new RoutingDecision("1", "route", type, List.of(), Set.of(), Set.of(), List.of(), "TEST",
                "registry", "policy", List.of(), true, 1);
    }
}
