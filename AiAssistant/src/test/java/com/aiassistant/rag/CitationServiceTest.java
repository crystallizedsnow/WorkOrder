package com.aiassistant.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CitationServiceTest {
    private final CitationService service = new CitationService();

    @Test
    void internalCitationAndSourceListAreRemovedFromAnswer() {
        RagContext context = service.prepare(List.of(evidence()));
        var result = service.validateAndRender("600 表示已验收。【来源S1】\n\n可信来源：\n- 内部来源", context);
        assertTrue(result.valid());
        assertEquals("600 表示已验收。", result.answer());
    }

    @Test
    void answerWithoutCitationPasses() {
        RagContext context = service.prepare(List.of(evidence()));
        var result = service.validateAndRender("600 表示已验收。", context);
        assertTrue(result.valid());
        assertEquals("600 表示已验收。", result.answer());
    }

    @Test
    void inventedCitationIsAlsoHiddenFromUser() {
        RagContext context = service.prepare(List.of(evidence()));
        var result = service.validateAndRender("内容。【来源S9】", context);
        assertTrue(result.valid());
        assertEquals("内容。", result.answer());
    }

    @Test
    void draftEvidenceNeverEntersPrompt() {
        Document draft = evidence();
        draft.setTrustLevel(TrustLevel.DRAFT);
        assertFalse(service.prepare(List.of(draft)).hasEvidence());
    }

    private Document evidence() {
        return Document.builder().id("chunk").text("600 表示已验收")
                .source("workorder-enums").sourceName("工单系统权威枚举")
                .sourceVersion("v1").headingPath("状态列表")
                .trustLevel(TrustLevel.AUTHORITATIVE).build();
    }
}
