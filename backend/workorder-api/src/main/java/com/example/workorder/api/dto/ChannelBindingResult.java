package com.example.workorder.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class ChannelBindingResult {
    private boolean bound;
    private String platform;
    private String tenantKey;
    private String unionId;
    private String maskedStaffNumber;
    private String maskedName;
    private String status;
}
