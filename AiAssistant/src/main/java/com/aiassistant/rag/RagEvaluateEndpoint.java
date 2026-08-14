package com.aiassistant.rag;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Endpoint(id = "ragevaluate")
@ConditionalOnProperty(prefix = "llm.embedding", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RagEvaluateEndpoint {
    private final RagEvaluationService evaluation;

    public RagEvaluateEndpoint(RagEvaluationService evaluation) { this.evaluation = evaluation; }

    @WriteOperation
    public Map<String, Object> evaluate() {
        RagEvaluationService.EvaluationResult result = evaluation.evaluate();
        return Map.of("accepted", true, "action", "evaluate", "total", result.total(),
                "hits", result.hits(), "recallAtK", result.recallAtK(), "misses", result.misses());
    }
}
