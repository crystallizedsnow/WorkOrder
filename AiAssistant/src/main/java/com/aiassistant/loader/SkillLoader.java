package com.aiassistant.loader;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class SkillLoader {

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
                    value = value.replace("\"", "").replace("'", "");
                    
                    switch (key.toLowerCase()) {
                        case "name":
                            metadata.setName(value);
                            break;
                        case "description":
                            metadata.setDescription(value);
                            break;
                        case "type":
                            metadata.setType(value);
                            break;
                        case "version":
                            metadata.setVersion(value);
                            break;
                    }
                }
            }
        }
        
        return metadata;
    }

    public List<SkillMetadata> listSkills() {
        return new ArrayList<>(skillMetadataMap.values());
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
        private String name;
        private String description;
        private String type;
        private String version;
        private String filePath;
    }
}