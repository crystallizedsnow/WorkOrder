package com.aiassistant.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RagStateServiceTest {
    @Test
    void failedBuildWithOldIndexIsDegraded() {
        RagStateService state = new RagStateService();
        state.ready(new RagBuildResult("v1", 4, 20));
        state.building();
        state.failed(new IllegalStateException("boom"), true);
        assertEquals(RagStatus.DEGRADED, state.snapshot().status());
        assertEquals("v1", state.snapshot().buildVersion());
    }

    @Test
    void failedInitialBuildIsFailed() {
        RagStateService state = new RagStateService();
        state.building();
        state.failed(new IllegalStateException("boom"), false);
        assertEquals(RagStatus.FAILED, state.snapshot().status());
    }
}
