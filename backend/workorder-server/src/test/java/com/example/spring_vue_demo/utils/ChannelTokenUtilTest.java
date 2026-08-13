package com.example.spring_vue_demo.utils;

import com.example.spring_vue_demo.config.AuthProperties;
import com.example.spring_vue_demo.entity.Staff;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class ChannelTokenUtilTest {
    @Test
    void channelTokenCarriesAuditableBindingContextWithoutPlatformIdentity() {
        AuthProperties properties = new AuthProperties();
        properties.setJwtSecret("local-dev-workorder-jwt-secret-2026-change-before-production");
        TokenUtil util = new TokenUtil(properties);
        Staff staff = new Staff(); staff.setId(9L); staff.setAuthVersion(2); staff.setName("测试用户");

        Claims claims = util.parseAccessToken(util.generateChannelAccessToken(
                staff, 12L, 4, "feishu-session-1", Instant.now().plusSeconds(60))).getBody();

        assertEquals("9", claims.getSubject());
        assertEquals("channel_proxy", claims.get("typ"));
        assertEquals("feishu", claims.get("src"));
        assertEquals(12L, ((Number) claims.get("bid")).longValue());
        assertEquals(4, ((Number) claims.get("bver")).intValue());
        assertEquals("feishu-session-1", claims.get("csid"));
        assertNull(claims.get("unionId")); assertNull(claims.get("openId")); assertNull(claims.get("name"));
    }
}
