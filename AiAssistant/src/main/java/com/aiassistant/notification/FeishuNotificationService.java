package com.aiassistant.notification;

import com.aiassistant.channel.FeishuChannelAdapter;
import com.aiassistant.channel.FeishuDeliveryException;
import com.aiassistant.channel.FeishuProperties;
import com.aiassistant.channel.ChannelRateLimiter;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Service
@ConditionalOnProperty(name = "workorder.channel.feishu.enabled", havingValue = "true")
public class FeishuNotificationService {
    private final FeishuChannelAdapter adapter;
    private final ThreadPoolExecutor executor;
    private final ChannelRateLimiter rateLimiter;
    private final Map<String, DelayedNotificationResult> completed = new ConcurrentHashMap<>();

    public FeishuNotificationService(FeishuChannelAdapter adapter, FeishuProperties properties,
                                     ChannelRateLimiter rateLimiter) {
        this.adapter = adapter;
        this.rateLimiter = rateLimiter;
        this.executor = new ThreadPoolExecutor(properties.getNotificationWorkerThreads(),
                properties.getNotificationWorkerThreads(), 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.getNotificationQueueCapacity()),
                r -> { Thread t = new Thread(r, "feishu-notification"); t.setDaemon(true); return t; },
                new ThreadPoolExecutor.AbortPolicy());
    }

    public DelayedNotificationResponse send(DelayedNotificationRequest request) {
        List<CompletableFuture<DelayedNotificationResult>> futures = request.notifications().stream()
                .map(item -> CompletableFuture.supplyAsync(() -> sendOne(item), executor)
                        .exceptionally(ex -> new DelayedNotificationResult(item.eventId(), "RETRYABLE_FAILED", null,
                                "QUEUE_OR_INTERNAL_ERROR", safeMessage(ex))))
                .toList();
        return new DelayedNotificationResponse(futures.stream().map(CompletableFuture::join).toList());
    }

    private DelayedNotificationResult sendOne(DelayedNotificationItem item) {
        DelayedNotificationResult prior = completed.get(item.eventId());
        if (prior != null) return prior;
        if (!rateLimiter.allow("proactive:" + item.tenantKey())) {
            return new DelayedNotificationResult(item.eventId(), "RETRYABLE_FAILED", null,
                    "LOCAL_RATE_LIMIT", "Local Feishu rate limit reached");
        }
        try {
            String messageId = adapter.sendCardToOpenId(item.openId(),
                    FeishuChannelAdapter.buildDelayedWorkOrderCard(item.workOrderCode(), item.title(), item.deadlineTime()));
            DelayedNotificationResult result = new DelayedNotificationResult(item.eventId(), "SUCCEEDED", messageId, null, null);
            completed.putIfAbsent(item.eventId(), result);
            return completed.get(item.eventId());
        } catch (FeishuDeliveryException ex) {
            return new DelayedNotificationResult(item.eventId(),
                    ex.isRetryable() ? "RETRYABLE_FAILED" : "PERMANENT_FAILED", null,
                    ex.getErrorCode(), safeMessage(ex));
        }
    }

    private static String safeMessage(Throwable ex) {
        String value = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    @PreDestroy public void shutdown() { executor.shutdown(); }
}
