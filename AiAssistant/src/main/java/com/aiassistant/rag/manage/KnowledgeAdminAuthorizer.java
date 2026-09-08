package com.aiassistant.rag.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class KnowledgeAdminAuthorizer {
    private final ObjectMapper json = new ObjectMapper();
    private final WebClient client = WebClient.builder().build();
    @Value("${workorder.backend.url:http://localhost:8080}") private String backendUrl;

    public String requireAdmin(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() == 7)
            throw new SecurityException("Authorization must use Bearer scheme");
        try {
            String body = client.get().uri(backendUrl + "/api/auth/validate")
                    .header(HttpHeaders.AUTHORIZATION, authorization).retrieve().bodyToMono(String.class).block();
            JsonNode result = json.readTree(body);
            if (!result.path("valid").asBoolean() || result.path("userId").asText().isBlank())
                throw new SecurityException("无效的访问令牌");
            if (!"admin".equalsIgnoreCase(result.path("role").asText()))
                throw new SecurityException("知识库管理操作仅允许管理员执行");
            return result.path("userId").asText();
        } catch (SecurityException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("管理员身份校验不可用", error);
        }
    }
}
