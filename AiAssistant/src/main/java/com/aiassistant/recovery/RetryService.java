package com.aiassistant.recovery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.Callable;

@Component
@Slf4j
public class RetryService {

    private static final long INITIAL_DELAY_MS = 1000;
    private static final double JITTER_FACTOR = 0.1;

    private final Random random = new Random();

    @Value("${workorder.agent.command-max-retries:2}")
    private int commandMaxRetries;

    public <T> T executeWithRetry(Callable<T> task, String taskName) throws Exception {
        return executeWithRetry(task, taskName, commandMaxRetries);
    }

    public <T> T executeWithRetry(Callable<T> task, String taskName, int maxRetries) throws Exception {
        Exception lastException = null;

        // maxRetries 表示首次执行失败后的重试次数，因此总尝试次数为 1 + maxRetries。
        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                return task.call();
            } catch (Exception e) {
                lastException = e;
                ErrorType errorType = new ErrorClassifier().classify(e);

                if (errorType != ErrorType.TRANSIENT) {
                    log.warn("非TRANSIENT错误，不重试: {}", e.getMessage());
                    throw e;
                }

                if (attempt <= maxRetries) {
                    long delay = calculateDelay(attempt);
                    log.info("任务 {} 第 {} 次尝试失败，{}ms后重试: {}", 
                            taskName, attempt, delay, e.getMessage());
                    Thread.sleep(delay);
                }
            }
        }

        log.error("任务 {} 重试 {} 次后仍失败", taskName, maxRetries);
        throw lastException;
    }

    private long calculateDelay(int attempt) {
        long baseDelay = (long) (INITIAL_DELAY_MS * Math.pow(2, attempt - 1));
        double jitter = baseDelay * JITTER_FACTOR;
        long randomJitter = (long) (random.nextDouble() * jitter * 2 - jitter);
        return baseDelay + randomJitter;
    }
}
