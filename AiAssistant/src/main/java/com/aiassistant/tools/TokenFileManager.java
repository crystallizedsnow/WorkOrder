package com.aiassistant.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class TokenFileManager {

    private static final String TOKEN_DIR = ".workorder";
    private static final String TOKEN_FILE = "token";
    private static final int TOKEN_EXPIRY_DAYS = 7;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    public TokenInfo getToken() {
        try {
            Path tokenFile = getTokenFilePath();
            if (!Files.exists(tokenFile)) {
                return null;
            }

            String content = Files.readString(tokenFile, StandardCharsets.UTF_8);
            TokenInfo tokenInfo = objectMapper.readValue(content, TokenInfo.class);

            if (isTokenExpired(tokenInfo)) {
                log.warn("Token已过期");
                return null;
            }

            return tokenInfo;
        } catch (IOException e) {
            log.error("读取Token失败", e);
            return null;
        }
    }

    public void saveToken(String token, String phone) {
        try {
            Path tokenDir = getTokenDirectory();
            if (!Files.exists(tokenDir)) {
                Files.createDirectories(tokenDir);
            }

            TokenInfo tokenInfo = new TokenInfo();
            tokenInfo.setToken(token);
            tokenInfo.setPhone(phone);
            tokenInfo.setExpireTime(OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(TOKEN_EXPIRY_DAYS));
            tokenInfo.setCreateTime(OffsetDateTime.now(ZoneOffset.ofHours(8)));

            Path tokenFile = getTokenFilePath();
            String content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tokenInfo);
            Files.writeString(tokenFile, content, StandardCharsets.UTF_8);

            log.info("Token已保存");
        } catch (IOException e) {
            log.error("保存Token失败", e);
        }
    }

    public boolean isLoggedIn() {
        TokenInfo tokenInfo = getToken();
        return tokenInfo != null && !isTokenExpired(tokenInfo);
    }

    private boolean isTokenExpired(TokenInfo tokenInfo) {
        if (tokenInfo.getExpireTime() == null) {
            return true;
        }
        return OffsetDateTime.now(ZoneOffset.ofHours(8)).isAfter(tokenInfo.getExpireTime());
    }

    private Path getTokenDirectory() {
        String homeDir = System.getProperty("user.home");
        return Paths.get(homeDir, TOKEN_DIR);
    }

    private Path getTokenFilePath() {
        return getTokenDirectory().resolve(TOKEN_FILE);
    }

    @Data
    public static class TokenInfo {
        private String token;
        private String phone;
        private OffsetDateTime expireTime;
        private OffsetDateTime createTime;
    }
}