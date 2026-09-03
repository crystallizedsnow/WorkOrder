package com.aiassistant.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FeishuIdentityClient {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient = WebClient.builder().build();
    @Value("${workorder.backend.url:http://localhost:8080}")
    private String backendUrl;
    @Value("${workorder.channel.service-key}")
    private String serviceKey;

    public BindingResult confirm(String code, String tenantKey, String unionId, String openId) {
        return bindingCall("/api/channel/identity/internal/confirm", Map.of(
                "code", code, "tenantKey", tenantKey, "unionId", unionId, "openId", openId == null ? "" : openId));
    }

    public BindingResult find(String tenantKey, String unionId, String openId) {
        return bindingCall("/api/channel/identity/internal/find", Map.of(
                "tenantKey", tenantKey, "unionId", unionId, "openId", openId == null ? "" : openId));
    }

    public ProxyToken exchange(String tenantKey, String unionId, String openId, String channelSessionId) {
        JsonNode data = call("/api/channel/identity/internal/exchange", Map.of(
                "tenantKey", tenantKey, "unionId", unionId, "openId", openId == null ? "" : openId,
                "channelSessionId", channelSessionId));
        return new ProxyToken(data.path("accessToken").asText(), Instant.parse(data.path("expiresAt").asText()),
                data.path("userId").asText());
    }

    private BindingResult bindingCall(String path, Map<String, String> body) {
        JsonNode data = call(path, body);
        return new BindingResult(data.path("bound").asBoolean(), data.path("maskedStaffNumber").asText(null),
                data.path("maskedName").asText(null), data.path("status").asText());
    }

    private JsonNode call(String path, Object body) {
        String response = webClient.post().uri(backendUrl + path).header("X-Workorder-Service-Key", serviceKey)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body).retrieve().bodyToMono(String.class).block();
        try {
            JsonNode root = objectMapper.readTree(response);
            if (root.path("code").asInt() != 1)
                throw new IllegalStateException(root.path("msg").asText("Channel identity request failed"));
            return root.path("data");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Invalid backend response", ex);
        }
    }

    public record BindingResult(boolean bound, String maskedStaffNumber, String maskedName, String status) {
    }

    public record ProxyToken(String accessToken, Instant expiresAt, String userId) {
    }
}
