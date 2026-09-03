package com.example.workorder.cli.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreviewPlanDTO {
    private String operation;
    private String operationCN;
    private String httpMethod;
    private String endpoint;
    private Map<String, Object> params;
    private List<ParamDisplayDTO> readableParams;
    private String impact;
    private String riskLevel;
    private boolean executable;
    private String schemaVersion;
    private String previewId;
    private String requestDigest;
    private boolean requiresConfirmation;
    private Instant expiresAt;
}
