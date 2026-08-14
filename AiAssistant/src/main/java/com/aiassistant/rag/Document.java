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

    /** 稳定片段 ID，同时作为 Elasticsearch _id。 */
    private String id;

    private String text;

    private String source;

    private String sourceName;

    private String sourceVersion;

    private String headingPath;

    @Builder.Default
    private TrustLevel trustLevel = TrustLevel.REVIEWED;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    public static Document of(String text) {
        return Document.builder().text(text).build();
    }

    public static Document of(String text, String source) {
        return Document.builder().text(text).source(source).build();
    }
}
