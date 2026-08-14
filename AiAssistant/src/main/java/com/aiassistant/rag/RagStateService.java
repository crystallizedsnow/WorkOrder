package com.aiassistant.rag;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class RagStateService {
    private final AtomicReference<Snapshot> state = new AtomicReference<>(
            new Snapshot(RagStatus.INITIALIZING, null, 0, 0, null, null, null));

    public Snapshot snapshot() { return state.get(); }

    public void building() {
        Snapshot old = state.get();
        state.set(new Snapshot(RagStatus.BUILDING, old.buildVersion(), old.documentCount(), old.chunkCount(),
                Instant.now(), old.lastSucceededAt(), null));
    }

    public void ready(RagBuildResult result) {
        state.set(new Snapshot(RagStatus.READY, result.buildVersion(), result.documentCount(), result.chunkCount(),
                state.get().lastStartedAt(), Instant.now(), null));
    }

    public void failed(Throwable error, boolean oldIndexAvailable) {
        Snapshot old = state.get();
        state.set(new Snapshot(oldIndexAvailable ? RagStatus.DEGRADED : RagStatus.FAILED,
                old.buildVersion(), old.documentCount(), old.chunkCount(), old.lastStartedAt(), old.lastSucceededAt(),
                error == null ? "unknown" : error.getMessage()));
    }

    public void disabled() {
        state.set(new Snapshot(RagStatus.DISABLED, null, 0, 0, null, null, null));
    }

    public record Snapshot(RagStatus status, String buildVersion, int documentCount, int chunkCount,
                           Instant lastStartedAt, Instant lastSucceededAt, String lastError) {}
}
