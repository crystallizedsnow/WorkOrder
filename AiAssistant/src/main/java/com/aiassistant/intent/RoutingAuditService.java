package com.aiassistant.intent;

import com.aiassistant.channel.model.AgentRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.stream.Collectors;

@Component
@Slf4j
public class RoutingAuditService {
    public void shadowDecision(AgentRequest request, RoutingDecision decision) {
        String candidates = decision.candidates().stream().map(candidate -> String.format(Locale.ROOT,
                        "%s:positive=%.4f,negative=%.4f,margin=%.4f", candidate.skillKey(),
                        candidate.positiveScore(), candidate.negativeScore(), candidate.scoreMargin()))
                .collect(Collectors.joining("|"));
        log.info("INTENT-SHADOW routeId={} traceId={} sessionId={} channel={} routeType={} skills={} "
                        + "reason={} embeddingAvailable={} candidates=[{}] registryVersion={} policyVersion={} durationMs={}",
                decision.routeId(), request.traceId(), request.sessionId(), request.channel(), decision.routeType(),
                decision.allowedSkillKeys(), decision.reasonCode(), decision.embeddingAvailable(), candidates,
                decision.registryVersion(), decision.policyVersion(), decision.durationMs());
    }

    public void shadowFailure(AgentRequest request, Throwable error, long durationMs) {
        log.warn("INTENT-SHADOW-FAILED traceId={} sessionId={} channel={} durationMs={} error={}",
                request.traceId(), request.sessionId(), request.channel(), durationMs,
                error == null ? "unknown" : safe(error.getMessage()));
    }

    private String safe(String value) {
        if (value == null) return "unknown";
        String normalized = value.replaceAll("[\\r\\n]+", " ");
        return normalized.length() > 300 ? normalized.substring(0, 300) : normalized;
    }
}
