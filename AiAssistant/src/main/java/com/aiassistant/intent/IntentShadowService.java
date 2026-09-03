package com.aiassistant.intent;

import com.aiassistant.channel.model.AgentRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

@Component
@Slf4j
public class IntentShadowService {
    private final IntentRoutingProperties properties;
    private final IntentShadowRouter router;
    private final RoutingAuditService audit;
    private final Executor executor;

    public IntentShadowService(IntentRoutingProperties properties, IntentShadowRouter router,
                               RoutingAuditService audit,
                               @Qualifier("intentRoutingExecutor") Executor executor) {
        this.properties = properties;
        this.router = router;
        this.audit = audit;
        this.executor = executor;
    }

    public void observe(AgentRequest request) {
        if (!properties.isEnabled() || properties.getMode() != IntentRoutingProperties.Mode.SHADOW) return;
        try {
            executor.execute(() -> execute(request));
        } catch (RuntimeException rejected) {
            log.warn("INTENT-SHADOW-SKIPPED traceId={} reason=queue_rejected", request.traceId());
        }
    }

    private void execute(AgentRequest request) {
        long started = System.nanoTime();
        try {
            audit.shadowDecision(request, router.route(request));
        } catch (Throwable error) {
            audit.shadowFailure(request, error, (System.nanoTime() - started) / 1_000_000);
        }
    }
}
