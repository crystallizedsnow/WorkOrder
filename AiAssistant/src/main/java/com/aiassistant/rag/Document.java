package com.aiassistant.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 知识库文档片段（替代 dev.langchain4j.data.segment.TextSegment / Document）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    private String text;

    private String source;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    public static Document of(String text) {
        return Document.builder().text(text).build();
    }

    public static Document of(String text, String source) {
        return Document.builder().text(text).source(source).build();
    }
}
