package com.aiassistant.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentEvaluationDatasetTest {
    @Test
    void datasetContainsExactlyTheApprovedFiftyCases() throws Exception {
        JsonNode root = new ObjectMapper().readTree(getClass().getResourceAsStream("/intent/golden-set.json"));
        assertEquals(50, root.size());
        Map<String, Integer> expected = Map.of("冒烟", 15, "缺失参数", 10, "近域", 10, "提示注入", 8, "能力之外", 7);
        java.util.Map<String, Integer> actual = new java.util.HashMap<>();
        Set<String> ids = new HashSet<>();
        root.forEach(item -> {
            assertTrue(ids.add(item.path("caseId").asText()));
            actual.merge(item.path("category").asText(), 1, Integer::sum);
            RouteType.valueOf(item.path("expectedRoute").asText());
        });
        assertEquals(expected, actual);
    }
}
