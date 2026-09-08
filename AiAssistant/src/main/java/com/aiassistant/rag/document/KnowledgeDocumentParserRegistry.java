package com.aiassistant.rag.document;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KnowledgeDocumentParserRegistry {
    private final List<KnowledgeDocumentParser> parsers;
    public KnowledgeDocumentParserRegistry(List<KnowledgeDocumentParser> parsers) { this.parsers = List.copyOf(parsers); }
    public KnowledgeDocumentParser require(String fileName, String contentType) {
        return parsers.stream().filter(parser -> parser.supports(fileName, contentType)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("只支持 .md 和 .docx 文档"));
    }
}
