package com.aiassistant.channel;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public class SessionExecutionCoordinator {
    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T execute(Long sessionId, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(sessionId, ignored -> new ReentrantLock(true));
        lock.lock();
        try { return action.get(); }
        finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) locks.remove(sessionId, lock);
        }
    }
}
