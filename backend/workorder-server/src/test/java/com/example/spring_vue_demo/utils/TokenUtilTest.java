package com.example.spring_vue_demo.utils;

import com.example.spring_vue_demo.config.AuthProperties;
import com.example.spring_vue_demo.entity.Staff;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TokenUtilTest {
    @Test
    void accessTokenContainsOnlyStableIdentityClaims() {
        AuthProperties properties = new AuthProperties();
        properties.setJwtSecret("test-secret-key-that-is-at-least-32-bytes-long");
        TokenUtil util = new TokenUtil(properties);
        Staff staff = new Staff();
        staff.setId(7L); staff.setAuthVersion(3); staff.setName("敏感姓名"); staff.setPhone("13800000000"); staff.setRole("admin");

        String token = util.generateAccessToken(staff, "session-1", Instant.now().plusSeconds(60));
        Claims claims = util.parseAccessToken(token).getBody();

        assertEquals("7", claims.getSubject());
        assertEquals("session-1", claims.get("sid"));
        assertEquals(3, ((Number) claims.get("ver")).intValue());
        assertNull(claims.get("name")); assertNull(claims.get("phone")); assertNull(claims.get("role"));
    }

    @Test
    void rejectsShortExternalSecret() {
        AuthProperties properties = new AuthProperties(); properties.setJwtSecret("short");
        assertThrows(IllegalStateException.class, () -> new TokenUtil(properties));
    }
}
