package com.aiassistant.rag.document;

import java.util.List;

public record ParsedKnowledgeDocument(String title, String normalizedText, List<String> warnings) {
    public ParsedKnowledgeDocument {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
