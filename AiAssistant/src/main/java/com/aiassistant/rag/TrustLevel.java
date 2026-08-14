package com.aiassistant.rag;

/** 知识来源可信等级。草稿内容不得进入生产检索。 */
public enum TrustLevel {
    AUTHORITATIVE,
    REVIEWED,
    DRAFT;

    public boolean citable() {
        return this == AUTHORITATIVE || this == REVIEWED;
    }
}
