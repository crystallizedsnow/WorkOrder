package com.aiassistant.rag;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ActiveRevisionRegistry {
    private final Map<String, String> active = new LinkedHashMap<>();
    private final AtomicLong version = new AtomicLong();

    public synchronized Snapshot snapshot() {
        return new Snapshot(version.get(), Map.copyOf(active));
    }

    public synchronized String activate(String documentId, String revisionId, String expectedRevisionId) {
        String current = active.get(documentId);
        if (expectedRevisionId != null && !java.util.Objects.equals(current, expectedRevisionId)) {
            throw new IllegalStateException("活动版本已经变化，请重新检查");
        }
        active.put(documentId, revisionId);
        version.incrementAndGet();
        return current;
    }

    public synchronized void seed(String documentId, String revisionId) {
        if (!active.containsKey(documentId)) {
            active.put(documentId, revisionId);
            version.incrementAndGet();
        }
    }

    public record Snapshot(long version, Map<String, String> revisions) {
        public Set<String> revisionIds() { return Set.copyOf(revisions.values()); }
        public Snapshot replacing(String documentId, String revisionId) {
            Map<String, String> copy = new LinkedHashMap<>(revisions);
            copy.put(documentId, revisionId);
            return new Snapshot(version, Map.copyOf(copy));
        }
    }
}
