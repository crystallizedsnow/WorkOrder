package com.aiassistant.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** 加载并严格校验可信知识来源清单。 */
@Component
public class KnowledgeManifestLoader {

    private final ResourceLoader resourceLoader;

    @Value("${knowledge-base.manifest:classpath:knowledge-base/manifest.properties}")
    private String manifestLocation;

    public KnowledgeManifestLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public Map<String, KnowledgeSource> load() {
        Resource resource = resourceLoader.getResource(manifestLocation);
        if (!resource.exists()) {
            throw new IllegalStateException("知识来源清单不存在: " + manifestLocation);
        }
        Properties properties = new Properties();
        try (InputStream input = resource.getInputStream()) {
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("知识来源清单加载失败: " + manifestLocation, e);
        }
        String rawIds = properties.getProperty("sources", "");
        List<String> ids = new ArrayList<>();
        for (String value : rawIds.split(",")) {
            if (!value.isBlank()) ids.add(value.trim());
        }
        if (ids.isEmpty()) throw new IllegalStateException("知识来源清单不能为空");

        Map<String, KnowledgeSource> result = new LinkedHashMap<>();
        for (String id : ids) {
            String prefix = "source." + id + ".";
            KnowledgeSource source = new KnowledgeSource(id,
                    properties.getProperty(prefix + "file"),
                    properties.getProperty(prefix + "name"),
                    properties.getProperty(prefix + "version"),
                    properties.getProperty(prefix + "owner"),
                    parseTrust(properties.getProperty(prefix + "trust")));
            source.validate();
            if (result.put(id, source) != null) {
                throw new IllegalArgumentException("知识来源 ID 重复: " + id);
            }
        }
        long distinctFiles = result.values().stream().map(KnowledgeSource::fileName).distinct().count();
        if (distinctFiles != result.size()) throw new IllegalArgumentException("知识来源文件重复注册");
        return result;
    }

    private TrustLevel parseTrust(String value) {
        try {
            return TrustLevel.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("未知知识可信等级: " + value, e);
        }
    }
}
