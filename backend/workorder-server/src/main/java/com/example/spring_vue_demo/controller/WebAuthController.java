package com.example.spring_vue_demo.controller;

import com.example.spring_vue_demo.config.AuthProperties;
import com.example.spring_vue_demo.param.LoginParam;
import com.example.spring_vue_demo.service.AuthTokenService;
import com.example.spring_vue_demo.service.LoginService;
import com.example.workorder.api.dto.AuthTokenResponse;
import com.example.workorder.api.dto.WebAccessTokenResponse;
import jakarta.validation.Valid;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Browser-only session adapter. Existing CLI and channel Bearer contracts remain unchanged. */
@RestController
@RequestMapping("/api/auth/web")
@RequiredArgsConstructor
public class WebAuthController {
    private final LoginService loginService;
    private final AuthTokenService authTokenService;
    private final AuthProperties properties;

    @PostMapping("/login")
    public ResponseEntity<WebAccessTokenResponse> login(@Valid @RequestBody LoginParam request) {
        try {
            return withRefreshCookie(loginService.loginToken(request));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, exception.getMessage());
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<WebAccessTokenResponse> refresh(HttpServletRequest request) {
        String refreshToken = cookieValue(request);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "浏览器会话不存在或已过期");
        }
        try {
            return withRefreshCookie(authTokenService.refresh(refreshToken));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, exception.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String refreshToken = cookieValue(request);
        authTokenService.logoutRefresh(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearCookie().toString()).build();
    }

    private ResponseEntity<WebAccessTokenResponse> withRefreshCookie(AuthTokenResponse token) {
        WebAccessTokenResponse body = new WebAccessTokenResponse(
                token.getTokenType(), token.getAccessToken(), token.getAccessTokenExpiresAt());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookie(token.getRefreshToken()).toString()).body(body);
    }

    private ResponseCookie refreshCookie(String value) {
        return cookie(value).maxAge(properties.getRefreshTtl()).build();
    }

    private ResponseCookie clearCookie() {
        return cookie("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String value) {
        return ResponseCookie.from(properties.getWebCookieName(), value)
                .httpOnly(true).secure(properties.isWebCookieSecure()).sameSite(properties.getWebCookieSameSite())
                .path("/");
    }

    private String cookieValue(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (properties.getWebCookieName().equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
