package com.example.workorder.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResolvedChannelBinding {
    private Long userId;
    private String tenantKey;
    private String openId;
}
