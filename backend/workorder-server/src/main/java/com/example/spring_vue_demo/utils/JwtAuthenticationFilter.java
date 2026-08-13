package com.example.spring_vue_demo.utils;

import com.example.spring_vue_demo.entity.Staff;
import com.example.spring_vue_demo.service.AuthTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final AuthTokenService authTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null) {
            Staff staff = authTokenService.authenticatedStaff(authorization);
            if (staff != null) {
                StaffHolder.set(staff);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        staff, null, Collections.singleton(() -> "ROLE_" + staff.getRole().toUpperCase()));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        try { chain.doFilter(request, response); } finally { StaffHolder.clear(); }
    }
}
