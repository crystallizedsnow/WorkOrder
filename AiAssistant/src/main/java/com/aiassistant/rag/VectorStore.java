package com.aiassistant.rag;

import java.util.List;

/**
 * 向量存储接口（替代 dev.langchain4j.store.embedding.EmbeddingStore）。
 */
public interface VectorStore {

    /** 批量入库：向量与文档一一对应 */
    void addAll(List<float[]> vectors, List<Document> documents);

    /** 单条入库 */
    void add(float[] vector, Document document);

    /** 相似度检索：返回 Top-K 且分数 >= minScore 的文档 */
    List<Document> search(float[] queryVector, int maxResults, double minScore);

    /** 清空索引（知识库重建时使用） */
    void clear();

    /** 存储是否可用（依赖的外部服务是否就绪） */
    boolean isAvailable();
}
