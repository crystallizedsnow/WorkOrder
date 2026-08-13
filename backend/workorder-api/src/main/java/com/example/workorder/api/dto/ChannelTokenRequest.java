package com.example.workorder.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChannelTokenRequest extends FeishuIdentityRequest {
    @NotBlank private String channelSessionId;
}
