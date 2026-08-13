package com.example.workorder.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidateTokenResult {
    private boolean valid;
    private String userId;
    private String role;
    private String sessionId;
    private Long expiresAtEpochSecond;
    private String message;

    public ValidateTokenResult(boolean valid, String userId, String role, String message) {
        this(valid, userId, role, null, null, message);
    }
}
