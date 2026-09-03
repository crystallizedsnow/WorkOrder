package com.example.workorder.cli.guard;

import com.example.workorder.cli.preview.WritePreview;
import com.example.workorder.cli.preview.WritePreviewStatus;
import com.example.workorder.cli.preview.WritePreviewStore;
import com.example.workorder.cli.service.WritePreviewManager;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class WriteExecutionGuard {
    private final WritePreviewManager previews;
    private final WritePreviewStore store;
    private final CanonicalRequestDigester digester;

    public WriteExecutionGuard(WritePreviewManager previews, WritePreviewStore store,
                               CanonicalRequestDigester digester) {
        this.previews = previews;
        this.store = store;
        this.digester = digester;
    }

    public AuthorizedWriteRequest authorizeAndClaim(String previewId, String dataCode,
                                                     Map<String, Object> params, String token) {
        if (previewId == null || previewId.isBlank())
            throw new WriteGuardException(WriteGuardErrorCode.PREVIEW_REQUIRED,
                    "该操作是写操作，必须先使用 --dry-run 完成预演，再由用户确认后执行。");
        String userId = previews.authenticatedUser(token);
        WritePreview value = previews.requireActive(previewId);
        if (!userId.equals(value.getUserId()))
            throw new WriteGuardException(WriteGuardErrorCode.PREVIEW_FORBIDDEN, "只能执行本人创建的预演");
        if (!value.getDataCode().equals(dataCode))
            throw new WriteGuardException(WriteGuardErrorCode.PREVIEW_MISMATCH, "正式执行的 dataCode 与预演不一致");
        String actual = digester.digest(userId, dataCode, value.getSchemaVersion(), params);
        if (!value.getRequestDigest().equals(actual))
            throw new WriteGuardException(WriteGuardErrorCode.PREVIEW_MISMATCH,
                    "正式执行参数与预演不一致，请使用当前参数重新预演");
        if (value.getStatus() != WritePreviewStatus.CONFIRMED) throw previews.statusError(value);
        WritePreview claimed = store.transition(previewId, WritePreviewStatus.CONFIRMED, WritePreviewStatus.EXECUTING)
                .orElseThrow(() -> previews.statusError(previews.requireActive(previewId)));
        claimed.setExecutionStartedAt(Instant.now());
        store.save(claimed, previews.remaining(claimed));
        return new AuthorizedWriteRequest(dataCode, value.getCanonicalParams(), claimed);
    }

    public void complete(WritePreview preview, boolean success, String summary) {
        WritePreviewStatus status = success ? WritePreviewStatus.SUCCEEDED : WritePreviewStatus.FAILED;
        WritePreview changed = store.transition(preview.getPreviewId(), WritePreviewStatus.EXECUTING, status)
                .orElse(preview);
        changed.setCompletedAt(Instant.now());
        changed.setResultSummary(summary == null ? "" : summary.substring(0, Math.min(500, summary.length())));
        store.save(changed, previews.remaining(changed));
    }
}
