package com.example.workorder.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FeishuIdentityRequest {
    @NotBlank private String tenantKey;
    @NotBlank private String unionId;
    private String openId;
}
