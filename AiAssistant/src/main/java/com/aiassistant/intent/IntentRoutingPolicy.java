package com.aiassistant.intent;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class IntentRoutingPolicy {
    private final SkillRegistry skillRegistry;
    private final IntentRoutingProperties properties;

    public IntentRoutingPolicy(SkillRegistry skillRegistry, IntentRoutingProperties properties) {
        this.skillRegistry = skillRegistry;
        this.properties = properties;
    }

    public RoutingDecision decide(IntentModelClient.ModelProposal proposal,
                                  SemanticSkillRetriever.RetrievalResult retrieval,
                                  long durationMs) {
        SkillRegistry.Snapshot registry = skillRegistry.snapshot();
        Set<String> candidateKeys = retrieval.candidates().stream()
                .map(RoutingDecision.SemanticCandidate::skillKey).collect(Collectors.toSet());
        RouteType routeType = proposal.routeType();
        List<RoutingDecision.IntentItem> intents = new ArrayList<>();
        Set<String> allowedSkills = new LinkedHashSet<>();
        Set<String> allowedTools = new LinkedHashSet<>();
        String reason = normalizeReason(proposal.reasonCode());

        if (routeType == RouteType.SKILL_EXECUTION) {
            for (IntentModelClient.ProposedIntent proposed : proposal.intents()) {
                SkillRegistry.SkillCard card = registry.skills().get(proposed.skillKey());
                if (card == null || !candidateKeys.contains(proposed.skillKey())) {
                    routeType = RouteType.CLARIFY;
                    reason = "MODEL_SELECTED_UNAVAILABLE_SKILL";
                    intents.clear(); allowedSkills.clear(); allowedTools.clear();
                    break;
                }
                if (allowedSkills.stream().anyMatch(card.conflictsWith()::contains)) {
                    routeType = RouteType.CLARIFY;
                    reason = "CONFLICTING_SKILLS";
                    intents.clear(); allowedSkills.clear(); allowedTools.clear();
                    break;
                }
                allowedSkills.add(card.key());
                allowedTools.addAll(card.allowedTools());
                intents.add(new RoutingDecision.IntentItem(card.key(), proposed.object(), proposed.action(),
                        card.riskLevel(), proposed.dependsOn()));
            }
            if (routeType == RouteType.SKILL_EXECUTION && intents.isEmpty()) {
                routeType = RouteType.CLARIFY;
                reason = "SKILL_EXECUTION_WITHOUT_INTENT";
            }
        }

        return new RoutingDecision("1.0", UUID.randomUUID().toString(), routeType, List.copyOf(intents),
                Set.copyOf(allowedSkills), Set.copyOf(allowedTools), proposal.missingInformation(), reason,
                registry.version(), properties.getPolicyVersion(), retrieval.candidates(),
                retrieval.embeddingAvailable(), durationMs);
    }

    public RoutingDecision control(String reasonCode, long durationMs) {
        return new RoutingDecision("1.0", UUID.randomUUID().toString(), RouteType.CONTROL_CONTINUATION,
                List.of(), Set.of(), Set.of(), List.of(), reasonCode, skillRegistry.snapshot().version(),
                properties.getPolicyVersion(), List.of(), false, durationMs);
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) return "MODEL_CLASSIFIED";
        String normalized = reason.toUpperCase().replaceAll("[^A-Z0-9_]+", "_");
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }
}
