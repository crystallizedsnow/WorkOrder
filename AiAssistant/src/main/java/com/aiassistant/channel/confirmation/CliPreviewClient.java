package com.aiassistant.channel.confirmation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class CliPreviewClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper;
    private final String baseUrl;

    public CliPreviewClient(ObjectMapper mapper,
                            @Value("${workorder.cli.service-url:http://localhost:5000}") String baseUrl) {
        this.mapper = mapper;
        this.baseUrl = baseUrl;
    }

    public void decide(String previewId, String accessToken, boolean confirm) {
        try {
            String body = mapper.writeValueAsString(java.util.Map.of("decision", confirm ? "CONFIRM" : "CANCEL"));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/write-previews/" + previewId + "/decision"))
                    .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = mapper.readTree(response.body());
            if (json.path("code").asInt(-1) != 0)
                throw new IllegalStateException("CLI Service拒绝确认: " + json.path("message").asText());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("确认预演被中断", interrupted);
        } catch (Exception error) {
            throw new IllegalStateException("确认预演失败: " + error.getMessage(), error);
        }
    }
}
