package com.example.spring_vue_demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.spring_vue_demo.config.AuthProperties;
import com.example.spring_vue_demo.entity.ChannelBindingChallenge;
import com.example.spring_vue_demo.entity.ExternalIdentityBinding;
import com.example.spring_vue_demo.entity.Staff;
import com.example.spring_vue_demo.mapper.ChannelBindingChallengeMapper;
import com.example.spring_vue_demo.mapper.ExternalIdentityBindingMapper;
import com.example.spring_vue_demo.mapper.StaffMapper;
import com.example.spring_vue_demo.utils.TokenUtil;
import com.example.workorder.api.dto.*;
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
import java.util.Objects;
import java.util.UUID;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChannelIdentityService {
    private static final String PLATFORM = "FEISHU";
    private final ChannelBindingChallengeMapper challengeMapper;
    private final ExternalIdentityBindingMapper bindingMapper;
    private final StaffMapper staffMapper;
    private final AuthTokenService authTokenService;
    private final TokenUtil tokenUtil;
    private final AuthProperties properties;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public BindingChallengeResponse createChallenge(String authorization) {
        Staff staff = requireUser(authorization);
        challengeMapper.update(null, new LambdaUpdateWrapper<ChannelBindingChallenge>()
                .eq(ChannelBindingChallenge::getUserId, staff.getId()).eq(ChannelBindingChallenge::getPlatform, PLATFORM)
                .eq(ChannelBindingChallenge::getStatus, "PENDING").set(ChannelBindingChallenge::getStatus, "CANCELLED"));
        String challengeId = UUID.randomUUID().toString();
        String secret = randomCode();
        String code = challengeId + "." + secret;
        Instant expiresAt = Instant.now().plus(properties.getBindingCodeTtl());
        ChannelBindingChallenge challenge = new ChannelBindingChallenge();
        challenge.setId(challengeId); challenge.setUserId(staff.getId()); challenge.setPlatform(PLATFORM);
        challenge.setCodeHash(hash(secret)); challenge.setStatus("PENDING"); challenge.setAttempts(0);
        challenge.setMaxAttempts(properties.getBindingMaxAttempts());
        challenge.setExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC)); challenge.setCreateTime(LocalDateTime.now(ZoneOffset.UTC));
        challengeMapper.insert(challenge);
        return new BindingChallengeResponse(code, expiresAt);
    }

    @Transactional
    public ChannelBindingResult confirm(String serviceKey, ConfirmBindingRequest request) {
        requireService(serviceKey);
        String[] codeParts = normalizeCode(request.getCode()).split("\\.", 2);
        if (codeParts.length != 2) throw new IllegalArgumentException("绑定码无效");
        ChannelBindingChallenge challenge = challengeMapper.selectById(codeParts[0]);
        if (challenge == null) throw new IllegalArgumentException("绑定码无效");
        if (!"PENDING".equals(challenge.getStatus()) || challenge.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new IllegalArgumentException("绑定码已失效");
        }
        if (!MessageDigest.isEqual(hash(codeParts[1]).getBytes(StandardCharsets.UTF_8), challenge.getCodeHash().getBytes(StandardCharsets.UTF_8))) {
            int attempt = challenge.getAttempts() + 1; challenge.setAttempts(attempt);
            if (attempt >= challenge.getMaxAttempts()) challenge.setStatus("LOCKED");
            challengeMapper.updateById(challenge);
            throw new IllegalArgumentException(attempt >= challenge.getMaxAttempts() ? "绑定码尝试次数已耗尽" : "绑定码无效");
        }
        Staff staff = staffMapper.selectById(challenge.getUserId());
        if (!active(staff)) throw new IllegalArgumentException("工单用户不可用");
        ExternalIdentityBinding identityBinding = anyBinding(request.getTenantKey(), request.getUnionId());
        if (identityBinding != null && !Objects.equals(identityBinding.getUserId(), staff.getId())) {
            if ("ACTIVE".equals(identityBinding.getStatus())) throw new IllegalArgumentException("该飞书身份已绑定其他工单用户");
        }
        ExternalIdentityBinding userBinding = bindingMapper.selectOne(new LambdaQueryWrapper<ExternalIdentityBinding>()
                .eq(ExternalIdentityBinding::getPlatform, PLATFORM).eq(ExternalIdentityBinding::getUserId, staff.getId())
                .eq(ExternalIdentityBinding::getStatus, "ACTIVE"));
        if (userBinding != null && (identityBinding == null || !Objects.equals(userBinding.getId(), identityBinding.getId()))) {
            throw new IllegalArgumentException("该工单用户已绑定其他飞书身份，请先解绑");
        }
        if (identityBinding == null) {
            identityBinding = new ExternalIdentityBinding(); identityBinding.setPlatform(PLATFORM);
            identityBinding.setTenantKey(request.getTenantKey()); identityBinding.setUnionId(request.getUnionId());
            identityBinding.setOpenId(request.getOpenId()); identityBinding.setUserId(staff.getId());
            identityBinding.setStatus("ACTIVE"); identityBinding.setBindingVersion(1);
            identityBinding.setBoundAt(LocalDateTime.now(ZoneOffset.UTC)); identityBinding.setCreateTime(LocalDateTime.now(ZoneOffset.UTC));
            identityBinding.setUpdateTime(LocalDateTime.now(ZoneOffset.UTC)); bindingMapper.insert(identityBinding);
        } else {
            identityBinding.setUserId(staff.getId()); identityBinding.setOpenId(request.getOpenId()); identityBinding.setStatus("ACTIVE");
            identityBinding.setBindingVersion(identityBinding.getBindingVersion() + 1); identityBinding.setBoundAt(LocalDateTime.now(ZoneOffset.UTC));
            identityBinding.setUnboundAt(null); identityBinding.setUpdateTime(LocalDateTime.now(ZoneOffset.UTC)); bindingMapper.updateById(identityBinding);
        }
        int consumed = challengeMapper.update(null, new LambdaUpdateWrapper<ChannelBindingChallenge>()
                .eq(ChannelBindingChallenge::getId, challenge.getId()).eq(ChannelBindingChallenge::getStatus, "PENDING")
                .set(ChannelBindingChallenge::getStatus, "USED").set(ChannelBindingChallenge::getUsedAt, LocalDateTime.now(ZoneOffset.UTC)));
        if (consumed != 1) throw new IllegalArgumentException("绑定码已被使用");
        return result(identityBinding, staff);
    }

    public ChannelBindingResult find(String serviceKey, FeishuIdentityRequest request) {
        requireService(serviceKey);
        ExternalIdentityBinding binding = activeBinding(request.getTenantKey(), request.getUnionId());
        if (binding == null) return new ChannelBindingResult(false, PLATFORM, request.getTenantKey(), request.getUnionId(), null, null, "UNBOUND");
        return result(binding, staffMapper.selectById(binding.getUserId()));
    }

    @Transactional
    public void unbind(String authorization) {
        Staff staff = requireUser(authorization);
        int changed = bindingMapper.update(null, new LambdaUpdateWrapper<ExternalIdentityBinding>()
                .eq(ExternalIdentityBinding::getPlatform, PLATFORM).eq(ExternalIdentityBinding::getUserId, staff.getId())
                .eq(ExternalIdentityBinding::getStatus, "ACTIVE").set(ExternalIdentityBinding::getStatus, "UNBOUND")
                .setSql("binding_version = binding_version + 1").set(ExternalIdentityBinding::getUnboundAt, LocalDateTime.now(ZoneOffset.UTC))
                .set(ExternalIdentityBinding::getUpdateTime, LocalDateTime.now(ZoneOffset.UTC)));
        if (changed == 0) throw new IllegalArgumentException("当前用户没有有效飞书绑定");
    }

    @Transactional
    public void disableBindingsForUser(Long userId) {
        bindingMapper.update(null, new LambdaUpdateWrapper<ExternalIdentityBinding>()
                .eq(ExternalIdentityBinding::getPlatform, PLATFORM).eq(ExternalIdentityBinding::getUserId, userId)
                .eq(ExternalIdentityBinding::getStatus, "ACTIVE").set(ExternalIdentityBinding::getStatus, "DISABLED")
                .setSql("binding_version = binding_version + 1").set(ExternalIdentityBinding::getUpdateTime, LocalDateTime.now(ZoneOffset.UTC)));
    }

    public ChannelTokenResponse exchange(String serviceKey, ChannelTokenRequest request) {
        requireService(serviceKey);
        ExternalIdentityBinding binding = activeBinding(request.getTenantKey(), request.getUnionId());
        if (binding == null) throw new IllegalArgumentException("飞书身份未绑定");
        Staff staff = staffMapper.selectById(binding.getUserId());
        if (!active(staff)) throw new IllegalArgumentException("工单用户不可用");
        Instant expiry = Instant.now().plus(properties.getChannelAccessTtl());
        String token = tokenUtil.generateChannelAccessToken(staff, binding.getId(), binding.getBindingVersion(), request.getChannelSessionId(), expiry);
        return new ChannelTokenResponse("Bearer", token, expiry, String.valueOf(staff.getId()));
    }

    public BatchResolveBindingResponse batchResolve(String serviceKey, BatchResolveBindingRequest request) {
        requireService(serviceKey);
        if (!PLATFORM.equalsIgnoreCase(request.getPlatform())) {
            throw new IllegalArgumentException("不支持的渠道平台");
        }
        List<Long> userIds = request.getUserIds().stream().filter(Objects::nonNull).distinct().toList();
        if (userIds.isEmpty()) {
            return new BatchResolveBindingResponse(List.of());
        }
        return new BatchResolveBindingResponse(bindingMapper.resolveActiveBindings(PLATFORM, userIds));
    }

    private Staff requireUser(String authorization) { Staff staff = authTokenService.authenticatedStaff(authorization); if (staff == null) throw new IllegalArgumentException("用户认证失败"); return staff; }
    private ExternalIdentityBinding activeBinding(String tenantKey, String unionId) { return bindingMapper.selectOne(new LambdaQueryWrapper<ExternalIdentityBinding>()
            .eq(ExternalIdentityBinding::getPlatform, PLATFORM).eq(ExternalIdentityBinding::getTenantKey, tenantKey)
            .eq(ExternalIdentityBinding::getUnionId, unionId).eq(ExternalIdentityBinding::getStatus, "ACTIVE")); }
    private ExternalIdentityBinding anyBinding(String tenantKey, String unionId) { return bindingMapper.selectOne(new LambdaQueryWrapper<ExternalIdentityBinding>()
            .eq(ExternalIdentityBinding::getPlatform, PLATFORM).eq(ExternalIdentityBinding::getTenantKey, tenantKey)
            .eq(ExternalIdentityBinding::getUnionId, unionId)); }
    private boolean active(Staff staff) { return staff != null && staff.getStatus() != null && staff.getStatus() == 0; }
    private void requireService(String key) { if (key == null || properties.getChannelServiceKey() == null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), properties.getChannelServiceKey().getBytes(StandardCharsets.UTF_8))) throw new SecurityException("服务身份无效"); }
    private String randomCode() { byte[] bytes = new byte[18]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String normalizeCode(String code) { return code == null ? "" : code.trim(); }
    private String hash(String value) { try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private ChannelBindingResult result(ExternalIdentityBinding b, Staff s) { return new ChannelBindingResult(true, PLATFORM, b.getTenantKey(), b.getUnionId(), mask(s.getStaffNumber()), maskName(s.getName()), b.getStatus()); }
    private String mask(String value) { if (value == null || value.length() < 3) return "***"; return value.substring(0, 2) + "***" + value.substring(value.length() - 1); }
    private String maskName(String value) { if (value == null || value.isEmpty()) return "***"; return value.substring(0, 1) + "**"; }
}
