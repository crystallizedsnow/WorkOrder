package com.aiassistant.channel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionExecutionCoordinatorTest {
    @Test void sameSessionIsSerialized() throws Exception {
        SessionExecutionCoordinator coordinator = new SessionExecutionCoordinator();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CopyOnWriteArrayList<Integer> order = new CopyOnWriteArrayList<>();
        CountDownLatch firstEntered = new CountDownLatch(1);
        Future<?> first = pool.submit(() -> coordinator.execute(1L, () -> {
            firstEntered.countDown();
            try { Thread.sleep(80); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            order.add(1); return null;
        }));
        firstEntered.await(1, TimeUnit.SECONDS);
        Future<?> second = pool.submit(() -> coordinator.execute(1L, () -> { order.add(2); return null; }));
        first.get(); second.get(); pool.shutdown();
        assertEquals(List.of(1, 2), order);
    }
}
