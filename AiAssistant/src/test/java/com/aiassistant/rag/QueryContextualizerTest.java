package com.aiassistant.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QueryContextualizerTest {
    private final QueryContextualizer contextualizer = new QueryContextualizer();

    @Test
    void shortFollowUpIncludesPreviousQuestion() {
        String result = contextualizer.contextualize("那 600 呢？", List.of("状态码 400 是什么意思？"));
        assertTrue(result.contains("状态码 400"));
        assertTrue(result.contains("600"));
    }

    @Test
    void completeQuestionIsUnchanged() {
        String query = "请解释工单状态码 600 的含义和下一步处理流程";
        assertEquals(query, contextualizer.contextualize(query, List.of("无关问题")));
    }
}
