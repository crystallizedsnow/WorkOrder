package com.aiassistant.rag;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Endpoint(id = "ragrebuild")
@ConditionalOnProperty(prefix = "llm.embedding", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RagRebuildEndpoint {
    private final RagLifecycleManager lifecycle;

    public RagRebuildEndpoint(RagLifecycleManager lifecycle) { this.lifecycle = lifecycle; }

    @WriteOperation
    public Map<String, Object> rebuild() {
        return Map.of("accepted", lifecycle.rebuildAsync(), "action", "rebuild");
    }
}
