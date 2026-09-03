package com.example.spring_vue_demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "workorder.auth")
public class AuthProperties {
    private String jwtSecret;
    private String issuer = "workorder-backend";
    private String audience = "workorder-api";
    private Duration accessTtl = Duration.ofMinutes(15);
    private Duration refreshTtl = Duration.ofDays(30);
    private Duration bindingCodeTtl = Duration.ofMinutes(5);
    private Duration channelAccessTtl = Duration.ofMinutes(5);
    private int bindingMaxAttempts = 5;
    private String channelServiceKey;
    /** Browser refresh cookie settings. Secure must be enabled outside local HTTP development. */
    private String webCookieName = "workorder_refresh";
    private boolean webCookieSecure = false;
    private String webCookieSameSite = "Lax";
    /** A disabled, non-login staff account used as the sender of scheduled system messages. */
    private Long systemSenderId;
}
