package com.example.spring_vue_demo.controller;

import com.example.spring_vue_demo.config.AuthProperties;
import com.example.spring_vue_demo.param.LoginParam;
import com.example.spring_vue_demo.service.AuthTokenService;
import com.example.spring_vue_demo.service.LoginService;
import com.example.workorder.api.dto.AuthTokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import jakarta.servlet.http.Cookie;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebAuthControllerTest {
    @Test
    void loginKeepsRefreshTokenOutOfBodyAndWritesHttpOnlyCookie() {
        LoginService loginService = mock(LoginService.class);
        AuthTokenService tokenService = mock(AuthTokenService.class);
        AuthProperties properties = properties();
        Instant now = Instant.now();
        when(loginService.loginToken(any())).thenReturn(
                new AuthTokenResponse("Bearer", "access", now.plusSeconds(900), "refresh-secret", now.plusSeconds(3600)));

        ResponseEntity<?> response = new WebAuthController(loginService, tokenService, properties)
                .login(new LoginParam());

        assertEquals(200, response.getStatusCode().value());
        assertFalse(response.getBody().toString().contains("refresh-secret"));
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(cookie);
        assertTrue(cookie.contains("workorder_refresh=refresh-secret"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
    }

    @Test
    void logoutRevokesRefreshFamilyAndClearsCookie() {
        LoginService loginService = mock(LoginService.class);
        AuthTokenService tokenService = mock(AuthTokenService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("workorder_refresh", "refresh"));
        ResponseEntity<Void> response = new WebAuthController(loginService, tokenService, properties()).logout(request);
        verify(tokenService).logoutRefresh("refresh");
        assertTrue(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE).contains("Max-Age=0"));
    }

    private AuthProperties properties() {
        AuthProperties value = new AuthProperties();
        value.setRefreshTtl(Duration.ofHours(1));
        value.setWebCookieName("workorder_refresh");
        value.setWebCookieSameSite("Lax");
        return value;
    }
}
