package com.aiassistant.rag;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** RAG 状态端点。写操作拆成无参数端点，避免依赖编译器的参数名元数据。 */
@Component
@Endpoint(id = "rag")
@ConditionalOnProperty(prefix = "llm.embedding", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RagEndpoint {
    private final RagStateService state;

    public RagEndpoint(RagStateService state) {
        this.state = state;
    }

    @ReadOperation
    public RagStateService.Snapshot status() { return state.snapshot(); }
}
