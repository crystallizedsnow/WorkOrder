package com.aiassistant.intent;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class DomainScopeRegistry {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${workorder.intent-routing.domain-scope:classpath:intent/domain-scope.json}")
    private Resource domainScopeResource;

    private volatile DomainScope snapshot;

    @PostConstruct
    void initialize() throws Exception {
        DomainScope loaded = MAPPER.readValue(domainScopeResource.getInputStream(), DomainScope.class);
        validate(loaded);
        snapshot = loaded;
        log.info("INTENT-DOMAIN-REGISTRY key={} topics={} positiveExamples={} negativeExamples={}",
                loaded.getKey(), loaded.getSupportedTopics().size(), loaded.getPositiveExamples().size(),
                loaded.getNegativeExamples().size());
    }

    public DomainScope snapshot() {
        return snapshot;
    }

    private void validate(DomainScope scope) {
        if (scope == null || blank(scope.getKey()) || blank(scope.getName()) || blank(scope.getDescription())) {
            throw new IllegalStateException("意图领域卡片缺少 key、name 或 description");
        }
        if (scope.getSupportedTopics().isEmpty() || scope.getPositiveExamples().isEmpty()
                || scope.getNegativeExamples().isEmpty()) {
            throw new IllegalStateException("意图领域卡片必须包含 supportedTopics、positiveExamples 和 negativeExamples");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    @Data
    public static class DomainScope {
        private String key;
        private String name;
        private String description;
        private List<String> supportedTopics = new ArrayList<>();
        private List<String> positiveExamples = new ArrayList<>();
        private List<String> negativeExamples = new ArrayList<>();
    }
}
