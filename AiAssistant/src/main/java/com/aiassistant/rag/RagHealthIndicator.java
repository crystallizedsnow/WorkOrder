package com.aiassistant.rag;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class RagHealthIndicator implements HealthIndicator {
    private final RagStateService state;

    public RagHealthIndicator(RagStateService state) { this.state = state; }

    @Override
    public Health health() {
        RagStateService.Snapshot value = state.snapshot();
        Health.Builder health = switch (value.status()) {
            case READY, DISABLED -> Health.up();
            case DEGRADED, INITIALIZING, BUILDING -> Health.status(value.status().name());
            case FAILED -> Health.down();
        };
        health.withDetail("status", value.status()).withDetail("documents", value.documentCount())
                .withDetail("chunks", value.chunkCount());
        if (value.buildVersion() != null) health.withDetail("buildVersion", value.buildVersion());
        if (value.lastStartedAt() != null) health.withDetail("lastStartedAt", value.lastStartedAt());
        if (value.lastSucceededAt() != null) health.withDetail("lastSucceededAt", value.lastSucceededAt());
        if (value.lastError() != null) health.withDetail("lastError", value.lastError());
        return health.build();
    }
}
