package com.aiassistant.channel;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;

@Component
public class ConversationExecutor {
    private final ExecutorService workers;
    private final Map<String, CompletableFuture<Void>> tails = new ConcurrentHashMap<>();

    public ConversationExecutor(@Value("${workorder.channel.worker-threads:8}") int threads) {
        this.workers = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "channel-worker"); t.setDaemon(true); return t;
        });
    }

    public void submit(String conversationKey, Runnable task) {
        tails.compute(conversationKey, (key, tail) -> {
            CompletableFuture<Void> base = tail == null ? CompletableFuture.completedFuture(null) : tail;
            CompletableFuture<Void> next = base.handle((ignored, error) -> null).thenRunAsync(task, workers);
            next.whenComplete((ignored, error) -> tails.remove(key, next));
            return next;
        });
    }

    @PreDestroy
    void close() { workers.shutdown(); }
}
