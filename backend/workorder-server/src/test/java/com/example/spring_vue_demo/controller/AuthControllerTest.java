package com.example.spring_vue_demo.controller;

import com.example.spring_vue_demo.service.AuthTokenService;
import com.example.workorder.api.dto.ValidateTokenResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {
    @Test
    void validateDelegatesBearerHeaderWithoutLoggingCredential() {
        AuthTokenService service = mock(AuthTokenService.class);
        when(service.validateBearer("Bearer access-token")).thenReturn(
                new ValidateTokenResult(true, "2", "user", "sid", 123L, "Token验证成功"));
        AuthController controller = new AuthController(service);

        ValidateTokenResult result = controller.validateToken("Bearer access-token");

        assertTrue(result.isValid());
        assertEquals("sid", result.getSessionId());
        verify(service).validateBearer("Bearer access-token");
    }

    @Test
    void invalidBearerIsReportedByAuthenticationService() {
        AuthTokenService service = mock(AuthTokenService.class);
        when(service.validateBearer("invalid")).thenReturn(
                new ValidateTokenResult(false, null, null, null, null, "Token无效或已过期"));
        assertFalse(new AuthController(service).validateToken("invalid").isValid());
    }
}
