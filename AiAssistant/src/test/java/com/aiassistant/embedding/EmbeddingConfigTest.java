package com.aiassistant.embedding;

import com.aiassistant.llm.LlmConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingConfigTest {
    @Test
    void disabledPropertyDoesNotCreateOrLoadOnnxModel() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("test", Map.of("llm.embedding.enabled", "false")));
            context.register(LlmConfig.class, EmbeddingConfig.class);
            context.refresh();
            assertTrue(context.getBeansOfType(OnnxEmbeddingModel.class).isEmpty());
        }
    }
}
