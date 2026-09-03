package com.example.spring_vue_demo.config;

import com.example.spring_vue_demo.utils.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.spring_vue_demo.service.AuthTokenService;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthTokenService authTokenService) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // ✅ 新写法
                .authorizeHttpRequests(auth -> auth
         //               .anyRequest().permitAll()
                        .requestMatchers("/user/login", "/api/auth/refresh", "/api/auth/web/login", "/api/auth/web/refresh", "/api/auth/web/logout", "/api/channel/identity/internal/**", "/doc.html", "/webjars/**", "/v3/api-docs/**", "/swagger-resources/**").permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new JwtAuthenticationFilter(authTokenService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
}
