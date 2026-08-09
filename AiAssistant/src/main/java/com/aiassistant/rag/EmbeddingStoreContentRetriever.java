package com.aiassistant.rag;

import com.aiassistant.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

/**
 * 基于向量存储的内容检索器（替代 dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever）。
 */
@Slf4j
public class EmbeddingStoreContentRetriever implements ContentRetriever {

    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final int maxResults;
    private final double minScore;

    public EmbeddingStoreContentRetriever(EmbeddingModel embeddingModel,
                                          VectorStore vectorStore,
                                          int maxResults,
                                          double minScore) {
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.maxResults = maxResults;
        this.minScore = minScore;
    }

    @Override
    public List<Document> retrieve(String query) {
        if (query == null || query.isEmpty()) {
            return Collections.emptyList();
        }
        if (embeddingModel == null || !embeddingModel.isAvailable()) {
            log.debug("向量化模型不可用，跳过 RAG 检索");
            return Collections.emptyList();
        }
        if (vectorStore == null || !vectorStore.isAvailable()) {
            log.debug("向量存储不可用，跳过 RAG 检索");
            return Collections.emptyList();
        }
        try {
            float[] vector = embeddingModel.embed(query);
            return vectorStore.search(vector, maxResults, minScore);
        } catch (Exception e) {
            log.warn("RAG 检索失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
