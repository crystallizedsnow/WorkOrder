package com.aiassistant.rag;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import org.springframework.core.task.TaskExecutor;

@Configuration
@ConditionalOnProperty(prefix = "llm.embedding", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RagOperationalConfig {
    @Bean(name = "ragTaskExecutor")
    public TaskExecutor ragTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("rag-build-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
