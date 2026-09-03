package com.aiassistant.intent;

import com.aiassistant.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Endpoint(id = "intentrouting")
public class IntentRoutingEndpoint {
    private final IntentRoutingProperties properties;
    private final SkillRegistry skillRegistry;
    private final DomainScopeRegistry domainScopeRegistry;
    private final ObjectProvider<EmbeddingModel> embeddingModels;

    public IntentRoutingEndpoint(IntentRoutingProperties properties, SkillRegistry skillRegistry,
                                 DomainScopeRegistry domainScopeRegistry,
                                 ObjectProvider<EmbeddingModel> embeddingModels) {
        this.properties = properties;
        this.skillRegistry = skillRegistry;
        this.domainScopeRegistry = domainScopeRegistry;
        this.embeddingModels = embeddingModels;
    }

    @ReadOperation
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", properties.isEnabled());
        result.put("mode", properties.getMode().name());
        result.put("policyVersion", properties.getPolicyVersion());
        result.put("registryVersion", skillRegistry.snapshot().version());
        result.put("skills", skillRegistry.snapshot().skills().keySet());
        result.put("domain", domainScopeRegistry.snapshot().getKey());
        EmbeddingModel embeddingModel = embeddingModels.getIfAvailable();
        result.put("embeddingAvailable", embeddingModel != null && embeddingModel.isAvailable());
        return result;
    }
}
