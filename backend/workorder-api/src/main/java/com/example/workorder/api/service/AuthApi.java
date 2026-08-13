package com.example.workorder.api.service;

import com.example.workorder.api.dto.ValidateTokenResult;
import com.example.workorder.api.dto.AuthTokenResponse;
import com.example.workorder.api.dto.RefreshTokenRequest;

public interface AuthApi {
    ValidateTokenResult validateToken(String token);
    AuthTokenResponse refresh(RefreshTokenRequest request);
    void logout(String authorization);
}
