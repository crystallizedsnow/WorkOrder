package com.example.workorder.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfirmBindingRequest extends FeishuIdentityRequest {
    @NotBlank private String code;
}
