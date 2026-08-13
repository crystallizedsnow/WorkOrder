package com.example.workorder.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data @NoArgsConstructor @AllArgsConstructor
public class ChannelTokenResponse {
    private String tokenType;
    private String accessToken;
    private Instant expiresAt;
    private String userId;
}
