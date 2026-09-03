package com.example.workorder.cli.guard;

import com.example.workorder.cli.preview.WritePreview;
import com.example.workorder.cli.preview.WritePreviewStatus;
import com.example.workorder.cli.preview.WritePreviewStore;
import com.example.workorder.cli.service.WritePreviewManager;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WriteExecutionGuardTest {
    @Test
    void rejectsWriteWithoutPreviewBeforeAnyWriteServiceCanRun() {
        WriteExecutionGuard guard = new WriteExecutionGuard(mock(WritePreviewManager.class),
                mock(WritePreviewStore.class), mock(CanonicalRequestDigester.class));
        WriteGuardException error = assertThrows(WriteGuardException.class,
                () -> guard.authorizeAndClaim(null, "work_order_create", Map.of(), "Bearer token"));
        assertEquals(46001, error.getErrorCode().code());
        assertTrue(error.getMessage().contains("--dry-run"));
    }

    @Test
    void rejectsChangedParametersAfterPreview() {
        WritePreviewManager manager = mock(WritePreviewManager.class);
        WritePreviewStore store = mock(WritePreviewStore.class);
        CanonicalRequestDigester digester = mock(CanonicalRequestDigester.class);
        WritePreview preview = preview("expected");
        when(manager.authenticatedUser("token")).thenReturn("u1");
        when(manager.requireActive("wp_1")).thenReturn(preview);
        when(digester.digest(eq("u1"), eq("work_order_create"), eq("1"), anyMap())).thenReturn("changed");
        WriteExecutionGuard guard = new WriteExecutionGuard(manager, store, digester);
        WriteGuardException error = assertThrows(WriteGuardException.class,
                () -> guard.authorizeAndClaim("wp_1", "work_order_create", Map.of("title", "changed"), "token"));
        assertEquals(46005, error.getErrorCode().code());
        verify(store, never()).transition(anyString(), any(), any());
    }

    @Test
    void atomicallyClaimsConfirmedPreviewAndUsesStoredCanonicalParams() {
        WritePreviewManager manager = mock(WritePreviewManager.class);
        WritePreviewStore store = mock(WritePreviewStore.class);
        CanonicalRequestDigester digester = mock(CanonicalRequestDigester.class);
        WritePreview preview = preview("digest");
        when(manager.authenticatedUser("token")).thenReturn("u1");
        when(manager.requireActive("wp_1")).thenReturn(preview);
        when(manager.remaining(any())).thenReturn(Duration.ofMinutes(5));
        when(digester.digest(eq("u1"), eq("work_order_create"), eq("1"), anyMap())).thenReturn("digest");
        when(store.transition("wp_1", WritePreviewStatus.CONFIRMED, WritePreviewStatus.EXECUTING))
                .thenAnswer(invocation -> { preview.setStatus(WritePreviewStatus.EXECUTING); return java.util.Optional.of(preview); });
        WriteExecutionGuard guard = new WriteExecutionGuard(manager, store, digester);
        AuthorizedWriteRequest result = guard.authorizeAndClaim("wp_1", "work_order_create",
                Map.of("title", "same"), "token");
        assertEquals(Map.of("title", "same"), result.params());
        verify(store).transition("wp_1", WritePreviewStatus.CONFIRMED, WritePreviewStatus.EXECUTING);
    }

    private WritePreview preview(String digest) {
        return WritePreview.builder().previewId("wp_1").userId("u1").dataCode("work_order_create")
                .canonicalParams(Map.of("title", "same")).requestDigest(digest).schemaVersion("1")
                .status(WritePreviewStatus.CONFIRMED).expiresAt(Instant.now().plusSeconds(300)).build();
    }
}
