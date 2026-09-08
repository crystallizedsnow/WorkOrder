package com.aiassistant.rag.document;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class MarkdownKnowledgeDocumentParser implements KnowledgeDocumentParser {
    public boolean supports(String fileName, String contentType) {
        return fileName != null && fileName.toLowerCase().endsWith(".md");
    }
    public String format() { return "MARKDOWN"; }
    public String version() { return "markdown-v1"; }
    public ParsedKnowledgeDocument parse(InputStream input) {
        try {
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            if (text.isBlank()) throw new IllegalArgumentException("Markdown 文档没有有效正文");
            String title = text.lines().filter(line -> line.startsWith("# ")).map(line -> line.substring(2).trim())
                    .findFirst().orElse("未命名文档");
            return new ParsedKnowledgeDocument(title, text, List.of());
        } catch (Exception e) {
            throw new IllegalArgumentException("Markdown 解析失败: " + e.getMessage(), e);
        }
    }
}
