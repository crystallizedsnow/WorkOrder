package com.example.workorder.cli.service;

import com.example.workorder.api.dto.ValidateTokenResult;
import com.example.workorder.cli.dto.response.PreviewPlanDTO;
import com.example.workorder.cli.guard.CanonicalRequestDigester;
import com.example.workorder.cli.guard.WriteGuardErrorCode;
import com.example.workorder.cli.guard.WriteGuardException;
import com.example.workorder.cli.preview.WritePreview;
import com.example.workorder.cli.preview.WritePreviewStatus;
import com.example.workorder.cli.preview.WritePreviewStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class WritePreviewManager {
    private final PreviewService previews;
    private final AuthService auth;
    private final CanonicalRequestDigester digester;
    private final WritePreviewStore store;
    private final Duration ttl;

    public WritePreviewManager(PreviewService previews, AuthService auth, CanonicalRequestDigester digester,
                               WritePreviewStore store,
                               @Value("${workorder.write-preview.ttl:PT10M}") Duration ttl) {
        this.previews = previews;
        this.auth = auth;
        this.digester = digester;
        this.store = store;
        this.ttl = ttl;
    }

    public PreviewPlanDTO create(String dataCode, Map<String, Object> params, String token, String traceId) {
        String userId = authenticatedUser(token);
        PreviewPlanDTO plan = previews.preview(dataCode, params);
        if (plan == null) throw new WriteGuardException(WriteGuardErrorCode.PREVIEW_INVALID,
                "该 dataCode 不是可预演的写操作: " + dataCode);
        Map<String, Object> canonical = digester.canonicalize(params);
        String digest = digester.digest(userId, dataCode, plan.getSchemaVersion(), canonical);
        Instant now = Instant.now();
        String previewId = "wp_" + UUID.randomUUID().toString().replace("-", "");
        WritePreview value = WritePreview.builder().previewId(previewId).userId(userId).dataCode(dataCode)
                .canonicalParams(canonical).requestDigest(digest).schemaVersion(plan.getSchemaVersion())
                .status(WritePreviewStatus.PREVIEWED).createdAt(now).expiresAt(now.plus(ttl)).traceId(traceId).build();
        store.save(value, ttl);
        plan.setParams(canonical);
        plan.setPreviewId(previewId);
        plan.setRequestDigest(digest);
        plan.setRequiresConfirmation(true);
        plan.setExpiresAt(value.getExpiresAt());
        return plan;
    }

    public WritePreview decide(String previewId, String token, boolean confirm) {
        String userId = authenticatedUser(token);
        WritePreview value = requireActive(previewId);
        if (!userId.equals(value.getUserId())) throw new WriteGuardException(WriteGuardErrorCode.PREVIEW_FORBIDDEN,
                "只能由创建预演的用户确认或取消");
        WritePreviewStatus target = confirm ? WritePreviewStatus.CONFIRMED : WritePreviewStatus.CANCELLED;
        WritePreview changed = store.transition(previewId, WritePreviewStatus.PREVIEWED, target)
                .orElseThrow(() -> statusError(value));
        if (confirm) changed.setConfirmedAt(Instant.now());
        store.save(changed, remaining(changed));
        return changed;
    }

    public String authenticatedUser(String token) {
        ValidateTokenResult result = auth.validateToken(token);
        if (result == null || !result.isValid() || result.getUserId() == null)
            throw new WriteGuardException(WriteGuardErrorCode.PREVIEW_FORBIDDEN, "用户凭证无效");
        return result.getUserId();
    }

    public WritePreview requireActive(String previewId) {
        WritePreview value = store.find(previewId).orElseThrow(() ->
                new WriteGuardException(WriteGuardErrorCode.PREVIEW_NOT_FOUND, "未找到对应预演，请重新执行 --dry-run"));
        if (value.getExpiresAt() == null || !value.getExpiresAt().isAfter(Instant.now()))
            throw new WriteGuardException(WriteGuardErrorCode.PREVIEW_EXPIRED, "预演已过期，请重新执行 --dry-run");
        return value;
    }

    public RuntimeException statusError(WritePreview value) {
        return switch (value.getStatus()) {
            case CANCELLED -> new WriteGuardException(WriteGuardErrorCode.PREVIEW_CANCELLED, "该预演已取消");
            case SUCCEEDED, FAILED, UNKNOWN, EXECUTING -> new WriteGuardException(
                    WriteGuardErrorCode.PREVIEW_ALREADY_CONSUMED, "该预演已经处理，不能重复执行");
            default -> new WriteGuardException(WriteGuardErrorCode.PREVIEW_NOT_CONFIRMED, "预演尚未获得用户确认");
        };
    }

    public Duration remaining(WritePreview value) {
        Duration duration = Duration.between(Instant.now(), value.getExpiresAt());
        return duration.isNegative() || duration.isZero() ? Duration.ofSeconds(1) : duration;
    }
}
