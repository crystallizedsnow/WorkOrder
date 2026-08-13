package com.example.workorder.cli.service;

import com.example.workorder.api.dto.ValidateTokenResult;
import com.example.workorder.cli.feign.AuthFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuthService {

    @Autowired
    private AuthFeignClient authFeignClient;

    public ValidateTokenResult validateToken(String token) {
        try {
            ResponseEntity<ValidateTokenResult> response = authFeignClient.validateToken(token);
            log.info("AuthFeignClient response status: {}", response.getStatusCode());
            if (response.getBody() != null) {
                log.info("Token validation result: valid={}, userId={}, role={}, message={}", 
                        response.getBody().isValid(), 
                        response.getBody().getUserId(), 
                        response.getBody().getRole(),
                        response.getBody().getMessage());
                return response.getBody();
            }
            log.warn("AuthFeignClient response body is null");
        } catch (Exception e) {
            log.error("Failed to validate token: {}, stack: {}", e.getMessage(), e.getStackTrace()[0]);
        }
        return new ValidateTokenResult(false, null, null, "Token validation failed");
    }

    public boolean verifyToken(String token) {
        ValidateTokenResult result = validateToken(token);
        return result.isValid();
    }

    public String getRoleFromToken(String token) {
        ValidateTokenResult result = validateToken(token);
        return result.getRole();
    }
}
