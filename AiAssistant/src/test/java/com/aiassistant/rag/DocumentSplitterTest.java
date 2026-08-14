package com.aiassistant.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentSplitterTest {
    private final DocumentSplitter splitter = new DocumentSplitter();

    @Test
    void stableIdIsDeterministicAndCarriesTrustMetadata() {
        Document source = Document.builder().text("# 状态\n\n600 表示已验收")
                .source("workorder-enums").sourceName("工单系统权威枚举")
                .sourceVersion("v1").headingPath("工单状态 > 状态列表")
                .trustLevel(TrustLevel.AUTHORITATIVE).build();

        List<Document> first = splitter.split(source);
        List<Document> second = splitter.split(source);

        assertEquals(first.stream().map(Document::getId).toList(), second.stream().map(Document::getId).toList());
        assertTrue(first.stream().allMatch(item -> item.getId().length() == 64));
        assertTrue(first.stream().allMatch(item -> item.getTrustLevel() == TrustLevel.AUTHORITATIVE));
        assertTrue(first.stream().allMatch(item -> "v1".equals(item.getSourceVersion())));
    }

    @Test
    void changedVersionProducesDifferentIds() {
        Document first = Document.builder().text("相同正文").source("s").sourceVersion("v1").build();
        Document second = Document.builder().text("相同正文").source("s").sourceVersion("v2").build();
        assertNotEquals(splitter.split(first).get(0).getId(), splitter.split(second).get(0).getId());
    }

    @Test
    void markdownHeadingPathAndTableStayTogether() {
        Document source = Document.builder().text("# 工单状态\n\n## 状态表\n\n|码|名称|\n|---|---|\n|600|已验收|")
                .source("s").sourceName("状态文档").sourceVersion("v1")
                .headingPath("工单状态").trustLevel(TrustLevel.AUTHORITATIVE).build();
        List<Document> chunks = splitter.split(source);
        Document table = chunks.stream().filter(item -> item.getText().contains("|600|已验收|")).findFirst().orElseThrow();
        assertEquals("工单状态 > 状态表", table.getHeadingPath());
        assertTrue(table.getText().contains("|码|名称|\n|---|---|\n|600|已验收|"));
    }
}
