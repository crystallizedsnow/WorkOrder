package com.aiassistant.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagHealthIndicatorTest {
    @Test
    void initialStateDoesNotAddNullHealthDetails() {
        var health = new RagHealthIndicator(new RagStateService()).health();
        assertEquals("INITIALIZING", health.getStatus().getCode());
        assertEquals(RagStatus.INITIALIZING, health.getDetails().get("status"));
    }
}
