package com.aiassistant.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class BackendAuthenticatedUserResolver implements AuthenticatedUserResolver {
    private final ObjectMapper mapper = new ObjectMapper();
    private final WebClient client = WebClient.builder().build();
    @Value("${workorder.backend.url:http://localhost:8080}") private String backendUrl;

    @Override
    public String resolve(String accessToken) {
        try {
            String body = client.get().uri(backendUrl + "/api/auth/validate")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).retrieve()
                    .bodyToMono(String.class).block();
            JsonNode result = mapper.readTree(body);
            if (!result.path("valid").asBoolean() || result.path("userId").asText().isBlank())
                throw new SecurityException(result.path("message").asText("Invalid access token"));
            return result.path("userId").asText();
        } catch (SecurityException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Unable to validate access token", error);
        }
    }
}
