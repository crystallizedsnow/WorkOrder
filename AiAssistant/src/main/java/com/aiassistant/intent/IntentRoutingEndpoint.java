package com.aiassistant.intent;

import com.aiassistant.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import com.aiassistant.channel.model.AgentRequest;
import com.aiassistant.channel.model.ChannelType;
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
    private final IntentShadowRouter router;

    public IntentRoutingEndpoint(IntentRoutingProperties properties, SkillRegistry skillRegistry,
                                 DomainScopeRegistry domainScopeRegistry,
                                 ObjectProvider<EmbeddingModel> embeddingModels,
                                 IntentShadowRouter router) {
        this.properties = properties;
        this.skillRegistry = skillRegistry;
        this.domainScopeRegistry = domainScopeRegistry;
        this.embeddingModels = embeddingModels;
        this.router = router;
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

    /** Protected management-network probe that executes the real semantic + LLM routing path. */
    @WriteOperation
    public RoutingDecision classify(String query) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query不能为空");
        return router.route(new AgentRequest(0L, "management-probe", query, "management-probe", ChannelType.WEB,
                null, null, "management-probe", "management-probe"));
    }
}
