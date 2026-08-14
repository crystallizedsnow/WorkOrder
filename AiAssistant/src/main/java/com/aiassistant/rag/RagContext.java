package com.aiassistant.rag;

import java.util.Collections;
import java.util.List;

/** 单次请求的结构化 RAG 证据及发给模型的只读上下文。 */
public record RagContext(String promptContext, List<Document> evidence) {
    public RagContext {
        evidence = evidence == null ? Collections.emptyList() : List.copyOf(evidence);
    }

    public static RagContext empty() {
        return new RagContext(null, Collections.emptyList());
    }

    public static RagContext notice(String message) {
        return new RagContext(message, Collections.emptyList());
    }

    public boolean hasEvidence() {
        return !evidence.isEmpty();
    }
}
