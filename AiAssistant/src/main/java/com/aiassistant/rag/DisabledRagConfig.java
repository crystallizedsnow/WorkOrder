package com.aiassistant.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

/** RAG 显式关闭时仅提供空检索器，不加载模型或连接 Elasticsearch。 */
@Configuration
@ConditionalOnProperty(prefix = "llm.embedding", name = "enabled", havingValue = "false")
public class DisabledRagConfig {
    @Bean
    public ContentRetriever disabledContentRetriever(RagStateService state) {
        state.disabled();
        return query -> Collections.emptyList();
    }
}
