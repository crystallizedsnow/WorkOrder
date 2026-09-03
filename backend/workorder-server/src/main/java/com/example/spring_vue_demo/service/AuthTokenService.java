package com.example.spring_vue_demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.spring_vue_demo.config.AuthProperties;
import com.example.spring_vue_demo.entity.RefreshSession;
import com.example.spring_vue_demo.entity.Staff;
import com.example.spring_vue_demo.mapper.RefreshSessionMapper;
import com.example.spring_vue_demo.mapper.StaffMapper;
import com.example.spring_vue_demo.mapper.ExternalIdentityBindingMapper;
import com.example.spring_vue_demo.entity.ExternalIdentityBinding;
import com.example.spring_vue_demo.utils.TokenUtil;
import com.example.workorder.api.dto.AuthTokenResponse;
import com.example.workorder.api.dto.ValidateTokenResult;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthTokenService {
    private final RefreshSessionMapper refreshSessionMapper;
    private final StaffMapper staffMapper;
    private final TokenUtil tokenUtil;
    private final AuthProperties properties;
    private final ExternalIdentityBindingMapper bindingMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public AuthTokenResponse issue(Staff staff) { return issue(staff, UUID.randomUUID().toString()); }

    private AuthTokenResponse issue(Staff staff, String familyId) {
        String rawRefresh = randomToken();
        RefreshSession session = new RefreshSession();
        session.setId(UUID.randomUUID().toString());
        session.setFamilyId(familyId);
        session.setUserId(staff.getId());
        session.setTokenHash(hash(rawRefresh));
        session.setAuthVersion(staff.getAuthVersion() == null ? 0 : staff.getAuthVersion());
        session.setCreateTime(LocalDateTime.now());
        Instant refreshExpiry = Instant.now().plus(properties.getRefreshTtl());
        session.setExpiresAt(LocalDateTime.ofInstant(refreshExpiry, ZoneOffset.UTC));
        refreshSessionMapper.insert(session);
        Instant accessExpiry = Instant.now().plus(properties.getAccessTtl());
        return new AuthTokenResponse("Bearer", tokenUtil.generateAccessToken(staff, session.getId(), accessExpiry),
                accessExpiry, rawRefresh, refreshExpiry);
    }

    @Transactional
    public AuthTokenResponse refresh(String rawRefresh) {
        RefreshSession old = findByHash(hash(rawRefresh));
        if (old == null || old.getRevokedAt() != null || old.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new IllegalArgumentException("刷新凭证无效或已过期");
        }
        if (old.getUsedAt() != null) {
            revokeFamily(old.getFamilyId());
            throw new IllegalArgumentException("检测到刷新凭证重放，会话已撤销");
        }
        Staff staff = staffMapper.selectById(old.getUserId());
        int currentVersion = staff == null || staff.getAuthVersion() == null ? 0 : staff.getAuthVersion();
        if (staff == null || staff.getStatus() == null || staff.getStatus() != 0 || currentVersion != old.getAuthVersion()) {
            revokeFamily(old.getFamilyId());
            throw new IllegalArgumentException("用户状态或认证版本已失效");
        }
        LocalDateTime usedAt = LocalDateTime.now(ZoneOffset.UTC);
        int claimed = refreshSessionMapper.update(null, new LambdaUpdateWrapper<RefreshSession>()
                .eq(RefreshSession::getId, old.getId()).isNull(RefreshSession::getUsedAt)
                .isNull(RefreshSession::getRevokedAt).set(RefreshSession::getUsedAt, usedAt));
        if (claimed != 1) {
            revokeFamily(old.getFamilyId());
            throw new IllegalArgumentException("检测到刷新凭证并发重放，会话已撤销");
        }
        old.setUsedAt(usedAt);
        AuthTokenResponse response = issue(staff, old.getFamilyId());
        RefreshSession replacement = findByHash(hash(response.getRefreshToken()));
        old.setReplacedBySessionId(replacement.getId());
        refreshSessionMapper.updateById(old);
        return response;
    }

    public ValidateTokenResult validateBearer(String authorization) {
        String raw = extractBearer(authorization);
        try {
            Claims claims = tokenUtil.parseAccessToken(raw).getBody();
            Staff staff = staffMapper.selectById(Long.valueOf(claims.getSubject()));
            String sessionId = claims.get("sid", String.class);
            int tokenVersion = ((Number) claims.get("ver")).intValue();
            int currentVersion = staff == null || staff.getAuthVersion() == null ? 0 : staff.getAuthVersion();
            if (staff == null || staff.getStatus() == null || staff.getStatus() != 0 || tokenVersion != currentVersion) {
                return invalid("用户状态或认证版本已失效");
            }
            String tokenType = claims.get("typ", String.class);
            if ("channel_proxy".equals(tokenType)) {
                ExternalIdentityBinding binding = bindingMapper.selectById(((Number) claims.get("bid")).longValue());
                int bindingVersion = ((Number) claims.get("bver")).intValue();
                if (binding == null || !"ACTIVE".equals(binding.getStatus())
                        || !staff.getId().equals(binding.getUserId()) || binding.getBindingVersion() != bindingVersion) {
                    return invalid("Channel绑定已失效");
                }
                sessionId = claims.get("csid", String.class);
            } else {
                RefreshSession session = refreshSessionMapper.selectById(sessionId);
                if (session == null || session.getRevokedAt() != null || session.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
                    return invalid("会话已失效");
                }
            }
            return new ValidateTokenResult(true, String.valueOf(staff.getId()), staff.getRole(),
                    sessionId, claims.getExpiration().toInstant().getEpochSecond(), "Token验证成功");
        } catch (RuntimeException ex) {
            return invalid("Token无效或已过期");
        }
    }

    @Transactional
    public void logout(String authorization) {
        Claims claims = tokenUtil.parseAccessToken(extractBearer(authorization)).getBody();
        String sessionId = claims.get("sid", String.class);
        RefreshSession session = refreshSessionMapper.selectById(sessionId);
        if (session != null) revokeFamily(session.getFamilyId());
    }

    @Transactional
    public void logoutRefresh(String rawRefresh) {
        if (rawRefresh == null || rawRefresh.isBlank()) return;
        RefreshSession session = findByHash(hash(rawRefresh));
        if (session != null) revokeFamily(session.getFamilyId());
    }

    public Staff authenticatedStaff(String authorization) {
        ValidateTokenResult result = validateBearer(authorization);
        return result.isValid() ? staffMapper.selectById(Long.valueOf(result.getUserId())) : null;
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        Staff staff = staffMapper.selectById(userId);
        if (staff == null) return;
        staff.setAuthVersion((staff.getAuthVersion() == null ? 0 : staff.getAuthVersion()) + 1);
        staffMapper.updateById(staff);
        refreshSessionMapper.update(null, new LambdaUpdateWrapper<RefreshSession>()
                .eq(RefreshSession::getUserId, userId).isNull(RefreshSession::getRevokedAt)
                .set(RefreshSession::getRevokedAt, LocalDateTime.now(ZoneOffset.UTC)));
    }

    public static String extractBearer(String value) {
        if (value == null || !value.startsWith("Bearer ") || value.length() == 7 || value.substring(7).contains(" ")) {
            throw new IllegalArgumentException("Authorization必须使用Bearer协议");
        }
        return value.substring(7);
    }

    private RefreshSession findByHash(String hash) {
        return refreshSessionMapper.selectOne(new LambdaQueryWrapper<RefreshSession>().eq(RefreshSession::getTokenHash, hash));
    }
    private void revokeFamily(String familyId) {
        refreshSessionMapper.update(null, new LambdaUpdateWrapper<RefreshSession>().eq(RefreshSession::getFamilyId, familyId)
                .isNull(RefreshSession::getRevokedAt).set(RefreshSession::getRevokedAt, LocalDateTime.now(ZoneOffset.UTC)));
    }
    private String randomToken() { byte[] value = new byte[32]; secureRandom.nextBytes(value); return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private String hash(String value) {
        try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private ValidateTokenResult invalid(String message) { return new ValidateTokenResult(false, null, null, null, null, message); }
}
