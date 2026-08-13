package com.example.spring_vue_demo.utils;

import com.example.spring_vue_demo.config.AuthProperties;
import com.example.spring_vue_demo.entity.Staff;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class TokenUtil {
    private final AuthProperties properties;
    private final SecretKey key;

    public TokenUtil(AuthProperties properties) {
        this.properties = properties;
        if (properties.getJwtSecret() == null || properties.getJwtSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("WORKORDER_JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        this.key = Keys.hmacShaKeyFor(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Staff staff, String sessionId, Instant expiresAt) {
        int authVersion = staff.getAuthVersion() == null ? 0 : staff.getAuthVersion();
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(String.valueOf(staff.getId()))
                .setId(java.util.UUID.randomUUID().toString())
                .setIssuer(properties.getIssuer())
                .setAudience(properties.getAudience())
                .claim("sid", sessionId)
                .claim("ver", authVersion)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiresAt))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateChannelAccessToken(Staff staff, Long bindingId, int bindingVersion,
                                             String channelSessionId, Instant expiresAt) {
        int authVersion = staff.getAuthVersion() == null ? 0 : staff.getAuthVersion();
        Instant now = Instant.now();
        return Jwts.builder().setSubject(String.valueOf(staff.getId())).setId(java.util.UUID.randomUUID().toString())
                .setIssuer(properties.getIssuer()).setAudience(properties.getAudience())
                .claim("typ", "channel_proxy").claim("src", "feishu")
                .claim("bid", bindingId).claim("bver", bindingVersion)
                .claim("csid", channelSessionId).claim("ver", authVersion)
                .setIssuedAt(Date.from(now)).setExpiration(Date.from(expiresAt))
                .signWith(key, SignatureAlgorithm.HS256).compact();
    }

    public Jws<Claims> parseAccessToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key).requireIssuer(properties.getIssuer())
                .requireAudience(properties.getAudience()).build().parseClaimsJws(token);
    }

    public boolean verifyToken(String token) {
        try { parseAccessToken(token); return true; } catch (RuntimeException ex) { return false; }
    }
}
