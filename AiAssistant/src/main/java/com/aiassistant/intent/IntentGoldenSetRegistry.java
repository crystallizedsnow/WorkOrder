package com.aiassistant.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class IntentGoldenSetRegistry implements SmartInitializingSingleton {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SkillRegistry skillRegistry;
    @Value("${workorder.intent-routing.golden-set:classpath:intent/golden-set.json}")
    private Resource goldenSetResource;

    public IntentGoldenSetRegistry(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            JsonNode root = MAPPER.readTree(goldenSetResource.getInputStream());
            if (!root.isArray() || root.isEmpty()) throw new IllegalStateException("Golden Set 必须是非空数组");
            Map<RouteType, Integer> counts = new EnumMap<>(RouteType.class);
            Set<String> registeredSkills = skillRegistry.snapshot().skills().keySet();
            int index = 0;
            for (JsonNode item : root) {
                if (item.path("query").asText().isBlank()) throw new IllegalStateException("第 " + index + " 项缺少 query");
                RouteType route = RouteType.valueOf(item.path("expectedRoute").asText());
                counts.merge(route, 1, Integer::sum);
                Set<String> expectedSkills = new HashSet<>();
                item.path("expectedSkills").forEach(value -> expectedSkills.add(value.asText()));
                if (!registeredSkills.containsAll(expectedSkills)) {
                    expectedSkills.removeAll(registeredSkills);
                    throw new IllegalStateException("第 " + index + " 项引用未知 Skill: " + expectedSkills);
                }
                index++;
            }
            log.info("INTENT-GOLDEN-SET cases={} distribution={}", root.size(), counts);
        } catch (Exception e) {
            throw new IllegalStateException("意图 Golden Set 校验失败", e);
        }
    }
}
