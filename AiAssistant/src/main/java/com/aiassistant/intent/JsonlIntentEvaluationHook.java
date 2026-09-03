package com.aiassistant.intent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "workorder.intent-routing.evaluation-hook", name = "enabled", havingValue = "true")
@Slf4j
public class JsonlIntentEvaluationHook implements IntentEvaluationHook {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Path output;

    public JsonlIntentEvaluationHook(IntentRoutingProperties properties) {
        this.output = Path.of(properties.getEvaluationHook().getOutputFile()).toAbsolutePath().normalize();
    }

    @Override
    public void onRequestStarted(String query) {
        write("request_started", Map.of("query", redact(query)));
    }

    @Override
    public void onRetrievalCompleted(SemanticSkillRetriever.RetrievalResult retrieval, long durationMs) {
        write("retrieval_completed", Map.of("retrieval", retrieval, "durationMs", durationMs));
    }

    @Override
    public void onModelCompleted(IntentModelClient.ModelProposal proposal, long durationMs) {
        write("model_completed", Map.of("proposal", proposal, "durationMs", durationMs));
    }

    @Override
    public void onPolicyCompleted(RoutingDecision decision, long durationMs) {
        write("policy_completed", Map.of("decision", decision, "durationMs", durationMs));
    }

    @Override
    public void onFailed(String stage, Throwable error, long durationMs) {
        write("failed", Map.of("stage", stage, "error", safe(error), "durationMs", durationMs));
    }

    private synchronized void write(String event, Object payload) {
        try {
            Path parent = output.getParent();
            if (parent != null) Files.createDirectories(parent);
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("timestamp", Instant.now().toString());
            record.put("event", event);
            record.put("payload", payload);
            Files.writeString(output, MAPPER.writeValueAsString(record) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.warn("INTENT-EVALUATION-HOOK-WRITE-FAILED event={} error={}", event, safe(e));
        }
    }

    private String redact(String value) {
        if (value == null) return "";
        return value.replaceAll("(?i)(bearer\\s+|token[=:：]\\s*)[A-Za-z0-9._-]+", "$1***")
                .replaceAll("1[3-9]\\d{9}", "***PHONE***");
    }

    private String safe(Throwable error) {
        if (error == null || error.getMessage() == null) return "unknown";
        String message = error.getClass().getSimpleName() + ": " + error.getMessage().replaceAll("[\\r\\n]+", " ");
        return message.length() > 300 ? message.substring(0, 300) : message;
    }
}
