package com.aiassistant.config;

import dev.langchain4j.community.model.zhipu.ZhipuAiChatModel;
import dev.langchain4j.community.model.zhipu.ZhipuAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZhipuConfig {

    @Value("${langchain4j.zhipu.api-key:}")
    private String apiKey;

    @Value("${langchain4j.zhipu.streaming-chat-model.model-name:glm-4.5-air}")
    private String modelName;

    @Value("${langchain4j.zhipu.streaming-chat-model.temperature:0.7}")
    private double temperature;

    @Bean(name = "zhipuStreamingChatModel")
    public ZhipuAiStreamingChatModel zhipuStreamingChatModel() {
        if (apiKey == null || apiKey.isEmpty() || apiKey.isBlank()) {
            throw new IllegalArgumentException("请配置环境变量 ZHIPU_API_KEY 或在 application.yaml 中设置 langchain4j.zhipu.api-key");
        }
        return ZhipuAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .model(modelName)
                .temperature(temperature)
                .build();
    }

    @Bean(name = "zhipuChatModel")
    public ZhipuAiChatModel zhipuChatModel() {
        if (apiKey == null || apiKey.isEmpty() || apiKey.isBlank()) {
            throw new IllegalArgumentException("请配置环境变量 ZHIPU_API_KEY 或在 application.yaml 中设置 langchain4j.zhipu.api-key");
        }
        return ZhipuAiChatModel.builder()
                .apiKey(apiKey)
                .model(modelName)
                .temperature(temperature)
                .build();
    }
}
