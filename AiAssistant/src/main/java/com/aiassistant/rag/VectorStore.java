package com.aiassistant.rag;

import java.util.List;
import java.util.Set;

/**
 * 向量存储接口（替代 dev.langchain4j.store.embedding.EmbeddingStore）。
 */
public interface VectorStore {

    /** 批量入库：向量与文档一一对应 */
    void addAll(List<float[]> vectors, List<Document> documents);

    /** 构建新版本并在全部写入成功后原子发布。 */
    default void publishAll(List<float[]> vectors, List<Document> documents, String buildVersion) {
        addAll(vectors, documents);
    }

    /** 单条入库 */
    void add(float[] vector, Document document);

    /** 相似度检索：返回 Top-K 且分数 >= minScore 的文档 */
    List<Document> search(float[] queryVector, int maxResults, double minScore);

    default List<Document> search(float[] queryVector, int maxResults, double minScore, Set<String> revisionIds) {
        return search(queryVector, maxResults, minScore);
    }

    /** 关键词检索；不支持时返回空集合。 */
    default List<Document> searchLexical(String query, int maxResults) {
        return List.of();
    }

    default List<Document> searchLexical(String query, int maxResults, Set<String> revisionIds) {
        return searchLexical(query, maxResults);
    }

    /** Append one immutable document revision without rebuilding unrelated documents. */
    default void addRevision(List<float[]> vectors, List<Document> documents) {
        addAll(vectors, documents);
    }

    /** 清空索引（知识库重建时使用） */
    void clear();

    /** 存储是否可用（依赖的外部服务是否就绪） */
    boolean isAvailable();

    default boolean hasPublishedIndex() { return isAvailable(); }

    /** 回滚到最近一个保留版本。 */
    default boolean rollback() { return false; }
}
