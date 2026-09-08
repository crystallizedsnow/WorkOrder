package com.aiassistant.rag.document;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DocxKnowledgeDocumentParserTest {
    @Test
    void parsesHeadingsListsAndTablesFromKnowledgeDocument() throws Exception {
        var parser = new DocxKnowledgeDocumentParser();
        try (var input = getClass().getResourceAsStream("/knowledge-base/计算机运维自助排障知识库.docx")) {
            var parsed = parser.parse(input);
            assertTrue(parsed.title().contains("计算机运维自助排障知识库"));
            assertTrue(parsed.normalizedText().contains("# 无法访问互联网或公司网页"));
            assertTrue(parsed.normalizedText().contains("- 查看任务栏网络图标"));
            assertTrue(parsed.normalizedText().contains("| 属性 | 内容 |"));
            assertTrue(parsed.normalizedText().contains("停止自助并创建工单"));
        }
    }
}
