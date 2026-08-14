package com.aiassistant.rag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagEndpointSignatureTest {
    @Test
    void actuatorWriteOperationsDoNotDependOnParameterNameMetadata() {
        for (Class<?> type : List.of(RagRebuildEndpoint.class, RagRollbackEndpoint.class, RagEvaluateEndpoint.class)) {
            for (var method : type.getDeclaredMethods()) {
                if (method.isAnnotationPresent(WriteOperation.class)) {
                    assertEquals(0, method.getParameterCount(), type.getSimpleName() + "." + method.getName());
                }
            }
        }
    }
}
