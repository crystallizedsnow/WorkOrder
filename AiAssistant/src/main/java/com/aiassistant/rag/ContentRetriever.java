package com.aiassistant.rag;

import java.util.List;
import java.util.Set;

/**
 * 内容检索器接口（替代 dev.langchain4j.rag.content.retriever.ContentRetriever）。
 */
public interface ContentRetriever {

    /** 根据查询文本检索相关知识库文档 */
    List<Document> retrieve(String query);

    default List<Document> retrieve(String query, Set<String> revisionIds) {
        return retrieve(query);
    }
}
