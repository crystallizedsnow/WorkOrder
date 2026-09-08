package com.aiassistant.rag.document;

import java.io.InputStream;

public interface KnowledgeDocumentParser {
    boolean supports(String fileName, String contentType);
    String format();
    String version();
    ParsedKnowledgeDocument parse(InputStream input);
}
