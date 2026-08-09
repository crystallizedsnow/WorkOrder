package com.aiassistant.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import lombok.Data;

/**
 * LLM 配置，统一从 application.yaml 读取 base-url / api-key / 模型参数。
 * 替代原 ZhipuConfig（不再依赖 langchain4j 的 ZhipuAiChatModel）。
 */
@Configuration
@ConfigurationProperties(prefix = "llm")
@Data
public class LlmConfig {

    private String provider = "zhipu";

    /** HTTP API 基础地址，例如 https://open.bigmodel.cn/api/paas/v4 */
    private String baseUrl;

    private String apiKey;

    private Chat chat = new Chat();

    private Embedding embedding = new Embedding();

    @Data
    public static class Chat {
        private String model = "glm-4.5-air";
        private double temperature = 0.7;
        private int maxTokens = 4096;
        /** 单次请求超时（毫秒） */
        private int timeoutMs = 60000;
    }

    @Data
    public static class Embedding {
        /** 是否启用 RAG 向量化 */
        private boolean enabled = true;
        /** 本地 ONNX 模型目录，例如 models/bge-small-zh-v1.5 */
        private String modelPath = "models/bge-small-zh-v1.5";
        /** ONNX 模型相对路径（相对 modelPath），例如 onnx/model；为空则取 modelPath 根目录 */
        private String modelName = "onnx/model";
        /** 向量维度，需与 ES 索引 dense_vector.dims 一致。BGE-small-zh-v1.5 为 512 */
        private int dimension = 512;
    }

    @Bean
    public RestClient llmRestClient() {
        return RestClient.builder()
                .baseUrl(baseUrl == null ? "" : baseUrl)
                .defaultHeader("Authorization", "Bearer " + (apiKey == null ? "" : apiKey))
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
