package com.aiassistant.intent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class IntentRoutingConfig {
    @Bean(name = "intentRoutingExecutor")
    public Executor intentRoutingExecutor(IntentRoutingProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getWorkerThreads());
        executor.setMaxPoolSize(properties.getWorkerThreads());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix("intent-shadow-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
