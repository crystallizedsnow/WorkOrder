package com.aiassistant.channel;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties("workorder.channel.feishu")
public class FeishuProperties {
    private boolean enabled;
    private String appId;
    private String appSecret;
    private int maxMessageLength = 4000;
    private int maxRequestsPerMinute = 30;
    private int sendRetries = 2;
    private Duration retryDelay = Duration.ofMillis(300);
    private int notificationBatchSize = 100;
    private int notificationWorkerThreads = 4;
    private int notificationQueueCapacity = 200;
}
