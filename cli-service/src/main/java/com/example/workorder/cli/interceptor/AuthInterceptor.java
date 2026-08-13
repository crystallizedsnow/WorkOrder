package com.example.workorder.cli.interceptor;

import com.example.workorder.api.dto.ValidateTokenResult;
import com.example.workorder.cli.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String STAFF_INFO_ATTR = "staffInfo";

    @Autowired
    @Lazy
    private AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        log.info("Authenticating request path: {}", request.getRequestURI());

        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing Authorization header");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Authorization must use Bearer scheme\",\"data\":null}");
            return false;
        }

        try {
            ValidateTokenResult result = authService.validateToken(authHeader);
            log.info("Token validation result: valid={}, userId={}, role={}, message={}", 
                    result.isValid(), result.getUserId(), result.getRole(), result.getMessage());
            
            if (!result.isValid()) {
                log.warn("Invalid access token");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"" + result.getMessage() + "\",\"data\":null}");
                return false;
            }

            request.setAttribute(STAFF_INFO_ATTR, result);
            return true;
        } catch (Exception e) {
            log.error("Token validation exception: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Token validation failed: " + e.getMessage() + "\",\"data\":null}");
            return false;
        }
    }
}
