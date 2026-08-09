package com.aiassistant.embedding;

import com.aiassistant.llm.LlmConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量化模型配置，基于 llm.embedding.* 配置项构建本地 ONNX 模型。
 * 替代原 LocalEmbeddingConfig（不再依赖 langchain4j 的 AllMiniLmL6V2EmbeddingModel）。
 */
@Configuration
@Slf4j
public class EmbeddingConfig {

    @Bean(destroyMethod = "close")
    public OnnxEmbeddingModel onnxEmbeddingModel(LlmConfig llmConfig) {
        LlmConfig.Embedding cfg = llmConfig.getEmbedding();
        if (!cfg.isEnabled()) {
            log.info("向量化模型已禁用（llm.embedding.enabled=false）");
            return new OnnxEmbeddingModel(cfg.getModelPath(), cfg.getModelName(), cfg.getDimension());
        }
        return new OnnxEmbeddingModel(cfg.getModelPath(), cfg.getModelName(), cfg.getDimension());
    }

    // @Bean
    // public EmbeddingModel embeddingModel(OnnxEmbeddingModel onnxEmbeddingModel) {
    //     return onnxEmbeddingModel;
    // }
}
