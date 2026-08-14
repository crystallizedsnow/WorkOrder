package com.aiassistant.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RagEvaluationServiceTest {
    @Test
    void goldenSetHasRequiredCoverageAndValidSources() {
        RagEvaluationService service = new RagEvaluationService(query -> List.of());
        var cases = service.loadCases();
        assertTrue(cases.size() >= 50);
        assertTrue(cases.stream().allMatch(item -> List.of("workorder-enums", "work-order-process",
                "system-overview", "response-format").contains(item.expectedSourceId())));
    }

    @Test
    void evaluationCalculatesRecall() {
        RagEvaluationService service = new RagEvaluationService(query -> List.of(
                Document.builder().source(query.contains("状态") || query.contains("优先级") || query.contains("类型码")
                                ? "workorder-enums" : "missing")
                        .trustLevel(TrustLevel.AUTHORITATIVE).build()));
        var result = service.evaluate();
        assertEquals(50, result.total());
        assertTrue(result.hits() > 0);
        assertTrue(result.recallAtK() > 0 && result.recallAtK() < 1);
    }
}
