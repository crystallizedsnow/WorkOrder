package com.example.spring_vue_demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Data
@Component
@ConfigurationProperties("workorder.notification")
public class DelayedNotificationProperties {
    private String aiAssistantUrl = "http://localhost:8081";
    private int batchSize = 100;
    private int maxAttempts = 3;
    private Duration initialRetryDelay = Duration.ofMinutes(1);
    private long dispatchDelayMs = 5000;
    private Duration sendingLease = Duration.ofMinutes(2);
}
