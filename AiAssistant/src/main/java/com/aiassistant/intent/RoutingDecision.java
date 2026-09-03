package com.aiassistant.intent;

import java.util.List;
import java.util.Set;

public record RoutingDecision(
        String schemaVersion,
        String routeId,
        RouteType routeType,
        List<IntentItem> intents,
        Set<String> allowedSkillKeys,
        Set<String> allowedToolNames,
        List<String> missingInformation,
        String reasonCode,
        String registryVersion,
        String policyVersion,
        List<SemanticCandidate> candidates,
        boolean embeddingAvailable,
        long durationMs) {

    public record IntentItem(String skillKey, String object, String action, String riskLevel,
                             List<Integer> dependsOn) {}

    public record SemanticCandidate(String skillKey, double positiveScore, double negativeScore,
                                    double scoreMargin) {}
}
