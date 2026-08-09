package com.aiassistant.embedding;

import java.util.List;

/**
 * 向量化模型接口（替代 dev.langchain4j.model.embedding.EmbeddingModel）。
 */
public interface EmbeddingModel {

    /** 单条文本向量化 */
    float[] embed(String text);

    /** 批量向量化 */
    List<float[]> embedAll(List<String> texts);

    /** 模型是否可用（加载失败时返回 false，RAG 自动降级） */
    boolean isAvailable();

    /** 向量维度 */
    int dimension();
}
