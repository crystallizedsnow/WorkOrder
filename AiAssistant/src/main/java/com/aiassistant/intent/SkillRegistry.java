package com.aiassistant.intent;

import com.aiassistant.agent.ToolDispatcher;
import com.aiassistant.loader.SkillLoader;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class SkillRegistry {
    private static final Set<String> RISK_LEVELS = Set.of("READ", "WRITE", "SENSITIVE");

    private final SkillLoader skillLoader;
    private final ToolDispatcher toolDispatcher;
    private volatile Snapshot snapshot = new Snapshot("uninitialized", Map.of());

    public SkillRegistry(SkillLoader skillLoader, ToolDispatcher toolDispatcher) {
        this.skillLoader = skillLoader;
        this.toolDispatcher = toolDispatcher;
    }

    @PostConstruct
    void initialize() {
        Map<String, SkillCard> published = new LinkedHashMap<>();
        skillLoader.getSkillMetadataMap().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    SkillLoader.SkillMetadata metadata = entry.getValue();
                    String key = metadata.getKey() == null || metadata.getKey().isBlank()
                            ? entry.getKey() : metadata.getKey();
                    validate(key, metadata);
                    if (metadata.isEnabled()) {
                        if (published.containsKey(key)) {
                            throw new IllegalStateException("重复的 Skill key: " + key);
                        }
                        published.put(key, toCard(key, metadata));
                    }
                });
        if (published.isEmpty()) {
            throw new IllegalStateException("没有可参与意图路由的已启用 Skill");
        }
        for (SkillCard card : published.values()) {
            for (String conflict : card.conflictsWith()) {
                if (card.key().equals(conflict) || !published.containsKey(conflict)) {
                    throw new IllegalStateException("Skill '" + card.key() + "' 的 conflictsWith 非法: " + conflict);
                }
            }
        }
        String version = versionOf(published);
        snapshot = new Snapshot(version, Map.copyOf(published));
        log.info("INTENT-SKILL-REGISTRY version={} enabledSkills={} registeredTools={}",
                version, published.keySet(), toolDispatcher.listTools().keySet());
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    private void validate(String key, SkillLoader.SkillMetadata metadata) {
        List<String> errors = new ArrayList<>();
        if (key == null || key.isBlank()) errors.add("key");
        if (metadata.getName() == null || metadata.getName().isBlank()) errors.add("name");
        if (metadata.getDescription() == null || metadata.getDescription().isBlank()) errors.add("description");
        if (metadata.getVersion() == null || metadata.getVersion().isBlank()) errors.add("version");
        if (metadata.getCapabilities().isEmpty()) errors.add("capabilities");
        if (metadata.getPositiveExamples().isEmpty()) errors.add("positiveExamples");
        if (metadata.getNegativeExamples().isEmpty()) errors.add("negativeExamples");
        if (metadata.getAllowedTools().isEmpty()) errors.add("allowedTools");
        if (!RISK_LEVELS.contains(metadata.getRiskLevel())) errors.add("riskLevel");
        for (String tool : metadata.getAllowedTools()) {
            if (!toolDispatcher.isToolRegistered(tool)) errors.add("unknownTool:" + tool);
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Skill '" + key + "' 注册信息非法: " + String.join(",", errors));
        }
    }

    private SkillCard toCard(String key, SkillLoader.SkillMetadata metadata) {
        return new SkillCard(key, metadata.getName(), metadata.getDescription(), metadata.getType(),
                metadata.getVersion(), List.copyOf(metadata.getCapabilities()),
                List.copyOf(metadata.getPositiveExamples()), List.copyOf(metadata.getNegativeExamples()),
                Set.copyOf(metadata.getAllowedTools()), metadata.getRiskLevel(),
                metadata.isRequiresConfirmation(), List.copyOf(metadata.getRequiredContext()),
                metadata.getPriority(), Set.copyOf(metadata.getConflictsWith()));
    }

    private String versionOf(Map<String, SkillCard> cards) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            cards.values().stream().sorted(Comparator.comparing(SkillCard::key))
                    .map(card -> String.join("|", card.key(), card.name(), card.description(), card.type(),
                            card.version(), String.join(",", card.capabilities()),
                            String.join(",", card.positiveExamples()), String.join(",", card.negativeExamples()),
                            card.allowedTools().stream().sorted().collect(java.util.stream.Collectors.joining(",")),
                            card.riskLevel(), Boolean.toString(card.requiresConfirmation()),
                            String.join(",", card.requiredContext()), Integer.toString(card.priority()),
                            card.conflictsWith().stream().sorted().collect(java.util.stream.Collectors.joining(","))))
                    .forEach(value -> digest.update(value.getBytes(StandardCharsets.UTF_8)));
            return "skills-" + java.util.HexFormat.of().formatHex(digest.digest()).substring(0, 12);
        } catch (Exception e) {
            throw new IllegalStateException("生成 Skill 注册版本失败", e);
        }
    }

    public record Snapshot(String version, Map<String, SkillCard> skills) {}

    public record SkillCard(String key, String name, String description, String type, String version,
                            List<String> capabilities, List<String> positiveExamples,
                            List<String> negativeExamples, Set<String> allowedTools,
                            String riskLevel, boolean requiresConfirmation,
                            List<String> requiredContext, int priority, Set<String> conflictsWith) {}
}
