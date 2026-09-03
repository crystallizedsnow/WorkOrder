package com.aiassistant.loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class SkillLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ResourcePatternResolver resourcePatternResolver;

    @Value("${workorder.cli.skill-pattern:classpath:skills/**/SKILL.md}")
    private String skillPathPattern;

    private final Map<String, SkillMetadata> skillMetadataMap = new HashMap<>();

    public SkillLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    @PostConstruct
    public void initialize() throws IOException {
        scanSkills();
        log.info("已加载 {} 个技能", skillMetadataMap.size());
    }

    private void scanSkills() throws IOException {
        skillMetadataMap.clear();
        Resource[] resources = resourcePatternResolver.getResources(skillPathPattern);
        
        if (resources == null || resources.length == 0) {
            log.warn("未找到技能文件: {}", skillPathPattern);
            return;
        }

        for (Resource resource : resources) {
            parseSkillFile(resource);
        }
    }

    private void parseSkillFile(Resource resource) {
        try {
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            SkillMetadata metadata = parseFrontmatter(content);
            metadata.setFilePath(resource.getURI().toString());
            
            String skillName = extractSkillName(resource);
            skillMetadataMap.put(skillName, metadata);
            
            log.debug("加载技能: {} ({})", skillName, metadata.getName());
        } catch (IOException e) {
            log.error("解析技能文件失败: {}", resource.getFilename(), e);
        }
    }

    private String extractSkillName(Resource resource) {
        try {
            String path = resource.getURI().getPath();
            int skillsIndex = path.indexOf("/skills/");
            if (skillsIndex > 0) {
                String remaining = path.substring(skillsIndex + "/skills/".length());
                int slashIndex = remaining.indexOf("/");
                if (slashIndex > 0) {
                    return remaining.substring(0, slashIndex);
                }
                return remaining;
            }
            return resource.getFilename();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private SkillMetadata parseFrontmatter(String content) {
        SkillMetadata metadata = new SkillMetadata();
        
        Pattern frontmatterPattern = Pattern.compile("---\\s*\\n([\\s\\S]*?)\\n---", Pattern.MULTILINE);
        Matcher matcher = frontmatterPattern.matcher(content);
        
        if (matcher.find()) {
            String frontmatter = matcher.group(1);
            String[] lines = frontmatter.split("\n");
            
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                int colonIndex = line.indexOf(":");
                if (colonIndex > 0) {
                    String key = line.substring(0, colonIndex).trim();
                    String value = line.substring(colonIndex + 1).trim();
                    String scalarValue = value.replace("\"", "").replace("'", "");
                    
                    switch (key.toLowerCase()) {
                        case "key":
                            metadata.setKey(scalarValue);
                            break;
                        case "name":
                            metadata.setName(scalarValue);
                            break;
                        case "description":
                            metadata.setDescription(scalarValue);
                            break;
                        case "type":
                            metadata.setType(scalarValue);
                            break;
                        case "version":
                            metadata.setVersion(scalarValue);
                            break;
                        case "enabled":
                            metadata.setEnabled(Boolean.parseBoolean(scalarValue));
                            break;
                        case "capabilities":
                            metadata.setCapabilities(parseList(value));
                            break;
                        case "positiveexamples":
                            metadata.setPositiveExamples(parseList(value));
                            break;
                        case "negativeexamples":
                            metadata.setNegativeExamples(parseList(value));
                            break;
                        case "allowedtools":
                            metadata.setAllowedTools(parseList(value));
                            break;
                        case "risklevel":
                            metadata.setRiskLevel(scalarValue.toUpperCase());
                            break;
                        case "requiresconfirmation":
                            metadata.setRequiresConfirmation(Boolean.parseBoolean(scalarValue));
                            break;
                        case "requiredcontext":
                            metadata.setRequiredContext(parseList(value));
                            break;
                        case "priority":
                            metadata.setPriority(parseInt(scalarValue, 0));
                            break;
                        case "conflictswith":
                            metadata.setConflictsWith(parseList(value));
                            break;
                    }
                }
            }
        }
        
        return metadata;
    }

    private List<String> parseList(String value) {
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        try {
            JsonNode node = MAPPER.readTree(value);
            if (node.isArray()) {
                List<String> result = new ArrayList<>();
                node.forEach(item -> {
                    if (item.isTextual() && !item.asText().isBlank()) {
                        result.add(item.asText().trim());
                    }
                });
                return result;
            }
        } catch (Exception ignored) {
            // 兼容逗号分隔的简易 frontmatter 写法。
        }
        List<String> result = new ArrayList<>();
        for (String item : value.split(",")) {
            String normalized = item.trim().replaceAll("^[\\[\\]\"']+|[\\[\\]\"']+$", "");
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public List<SkillMetadata> listSkills() {
        return Collections.unmodifiableList(new ArrayList<>(skillMetadataMap.values()));
    }

    public Map<String, SkillMetadata> getSkillMetadataMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(skillMetadataMap));
    }

    public String getSkillIndex() {
        StringBuilder sb = new StringBuilder();
        sb.append("可用技能列表:\n");
        
        for (Map.Entry<String, SkillMetadata> entry : skillMetadataMap.entrySet()) {
            SkillMetadata meta = entry.getValue();
            sb.append(String.format("- [%s] %s — %s\n", 
                    entry.getKey(), meta.getName(), meta.getDescription()));
        }
        
        return sb.toString();
    }

    public String loadSkillContent(String skillName) throws IOException {
        SkillMetadata metadata = skillMetadataMap.get(skillName);
        if (metadata == null) {
            throw new RuntimeException("技能不存在: " + skillName);
        }
        
        Resource resource = resourcePatternResolver.getResource(metadata.getFilePath());
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Data
    public static class SkillMetadata {
        private String key;
        private String name;
        private String description;
        private String type;
        private String version;
        private String filePath;
        private boolean enabled = true;
        private List<String> capabilities = new ArrayList<>();
        private List<String> positiveExamples = new ArrayList<>();
        private List<String> negativeExamples = new ArrayList<>();
        private List<String> allowedTools = new ArrayList<>();
        private String riskLevel = "READ";
        private boolean requiresConfirmation;
        private List<String> requiredContext = new ArrayList<>();
        private int priority;
        private List<String> conflictsWith = new ArrayList<>();
    }
}
