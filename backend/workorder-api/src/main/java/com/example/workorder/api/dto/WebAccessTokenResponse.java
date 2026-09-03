package com.example.workorder.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Access token response for browser sessions. Refresh credentials stay in an HttpOnly cookie. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebAccessTokenResponse {
    private String tokenType;
    private String accessToken;
    private Instant accessTokenExpiresAt;
}
