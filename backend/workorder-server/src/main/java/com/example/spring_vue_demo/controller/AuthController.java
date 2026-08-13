package com.example.spring_vue_demo.controller;

import com.example.spring_vue_demo.entity.Result;
import com.example.spring_vue_demo.service.AuthTokenService;
import com.example.workorder.api.dto.RefreshTokenRequest;
import com.example.workorder.api.dto.ValidateTokenResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthTokenService authTokenService;

    @GetMapping("/validate")
    public ValidateTokenResult validateToken(@RequestHeader("Authorization") String authorization) {
        return authTokenService.validateBearer(authorization);
    }

    @PostMapping("/refresh")
    public Result refresh(@Valid @RequestBody RefreshTokenRequest request) {
        try { return Result.success(authTokenService.refresh(request.getRefreshToken())); }
        catch (IllegalArgumentException ex) { return Result.error(ex.getMessage()); }
    }

    @PostMapping("/logout")
    public Result logout(@RequestHeader("Authorization") String authorization) {
        authTokenService.logout(authorization);
        return Result.success();
    }
}
