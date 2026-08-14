package com.aiassistant.rag;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeManifestLoaderTest {
    @Test
    void productionManifestContainsOnlyCitableUniqueSources() {
        KnowledgeManifestLoader loader = new KnowledgeManifestLoader(new DefaultResourceLoader());
        ReflectionTestUtils.setField(loader, "manifestLocation", "classpath:knowledge-base/manifest.properties");
        var sources = loader.load();
        assertFalse(sources.isEmpty());
        assertTrue(sources.values().stream().allMatch(source -> source.trustLevel().citable()));
        assertEquals(sources.size(), sources.values().stream().map(KnowledgeSource::fileName).distinct().count());
        assertEquals("工单系统权威枚举", sources.get("workorder-enums").displayName());
    }
}
