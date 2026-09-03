package com.example.workorder.cli.preview;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WritePreview {
    private String previewId;
    private String userId;
    private String dataCode;
    private Map<String, Object> canonicalParams;
    private String requestDigest;
    private String schemaVersion;
    private WritePreviewStatus status;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant confirmedAt;
    private Instant executionStartedAt;
    private Instant completedAt;
    private String traceId;
    private String resultSummary;
}
