package com.example.spring_vue_demo.controller;

import com.example.spring_vue_demo.entity.Staff;
import com.example.spring_vue_demo.utils.LogUtils;
import com.example.spring_vue_demo.utils.TokenUtil;
import com.example.workorder.api.dto.ValidateTokenResult;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/validate")
    public ValidateTokenResult validateToken(@RequestHeader("Authorization") String token) {
        String traceId = MDC.get("traceId");
        Map<String, Object> params = new HashMap<>();
        params.put("token", token != null ? "***" : null);
        LogUtils.entrance(traceId, "/api/auth/validate", "GET", params);
        
        if (token == null || token.trim().isEmpty()) {
            LogUtils.warn(traceId, "/api/auth/validate", "Token is null or empty");
            return new ValidateTokenResult(false, null, null, "Token不能为空");
        }
        
        boolean verified = TokenUtil.verifyToken(token);
        
        if (verified) {
            try {
                Staff staff = TokenUtil.parsestaffFromToken(token);
                ValidateTokenResult result = new ValidateTokenResult(true, String.valueOf(staff.getId()), staff.getRole(), "Token验证成功");
                LogUtils.returnLog(traceId, "/api/auth/validate", "GET", result);
                return result;
            } catch (Exception e) {
                LogUtils.error(traceId, "/api/auth/validate", params, e);
                return new ValidateTokenResult(false, null, null, "Token解析失败: " + e.getMessage());
            }
        } else {
            LogUtils.warn(traceId, "/api/auth/validate", "Token verification failed");
            return new ValidateTokenResult(false, null, null, "Token无效或已过期");
        }
    }
}