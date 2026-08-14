package com.aiassistant.rag;

/** 经过注册的知识来源。 */
public record KnowledgeSource(
        String sourceId,
        String fileName,
        String displayName,
        String version,
        String owner,
        TrustLevel trustLevel
) {
    public void validate() {
        if (blank(sourceId) || blank(fileName) || blank(displayName) || blank(version) || blank(owner)) {
            throw new IllegalArgumentException("知识来源清单字段不完整: " + sourceId);
        }
        if (trustLevel == null || !trustLevel.citable()) {
            throw new IllegalArgumentException("非可信知识来源不能发布: " + sourceId);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
