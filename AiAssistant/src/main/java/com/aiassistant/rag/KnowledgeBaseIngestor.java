package com.aiassistant.rag;

import com.aiassistant.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 知识库导入器：启动时加载 classpath 下的 Markdown 文档，切分、向量化后写入向量存储。
 * <p>
 * 替代原 com.aiassistant.ingestor.KnowledgeBaseIngestor（不再依赖 langchain4j EmbeddingStoreIngestor）。
 * 当向量化模型或向量存储不可用时自动跳过，保证应用可启动。
 */
@Component
@ConditionalOnProperty(prefix = "llm.embedding", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class KnowledgeBaseIngestor {

    @Value("${knowledge-base.directory:classpath:knowledge-base/}")
    private String knowledgeBaseDirectory;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private DocumentSplitter documentSplitter;

    @Autowired
    private ResourcePatternResolver resourcePatternResolver;

    @Autowired
    private KnowledgeManifestLoader manifestLoader;

    /** 由 RagLifecycleManager 在受管线程中调用。 */
    public RagBuildResult ingest() {
        try {
                if (embeddingModel == null || !embeddingModel.isAvailable()) {
                    throw new IllegalStateException("向量化模型不可用");
                }
                if (vectorStore == null || !vectorStore.isAvailable()) {
                    throw new IllegalStateException("向量存储不可用");
                }

                Map<String, KnowledgeSource> manifest = manifestLoader.load();
                List<Document> documents = loadDocumentsFromClasspath(manifest);
                if (documents.isEmpty()) {
                    throw new IllegalStateException("未找到可信知识库文档");
                }

                List<Document> segments = new ArrayList<>();
                for (Document doc : documents) {
                    segments.addAll(documentSplitter.split(doc));
                }
                log.info("知识库切分完成: {} 个文档 -> {} 个片段", documents.size(), segments.size());

                List<String> texts = new ArrayList<>();
                for (Document seg : segments) {
                    texts.add(seg.getText());
                }

                List<float[]> vectors = embeddingModel.embedAll(texts);
                String buildVersion = buildVersion(documents);
                vectorStore.publishAll(vectors, segments, buildVersion);
                log.info("知识库文档导入完成，共导入 {} 个片段", segments.size());
                return new RagBuildResult(buildVersion, documents.size(), segments.size());
        } catch (Exception e) {
            throw new IllegalStateException("知识库文档导入失败", e);
        }
    }

    private List<Document> loadDocumentsFromClasspath(Map<String, KnowledgeSource> manifest) {
        List<Document> documents = new ArrayList<>();
        try {
            Map<String, Resource> resourcesByName = new HashMap<>();
            Resource[] resources = resourcePatternResolver.getResources(knowledgeBaseDirectory + "**/*.md");
            for (Resource resource : resources) resourcesByName.put(resource.getFilename(), resource);
            for (KnowledgeSource sourceDefinition : manifest.values()) {
                Resource resource = resourcesByName.get(sourceDefinition.fileName());
                if (resource == null) {
                    throw new IllegalStateException("清单中的知识文件不存在: " + sourceDefinition.fileName());
                }
                try (var input = resource.getInputStream()) {
                    String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    Document document = Document.builder()
                            .text(content)
                            .source(sourceDefinition.sourceId())
                            .sourceName(sourceDefinition.displayName())
                            .sourceVersion(sourceDefinition.version())
                            .headingPath(firstHeading(content))
                            .trustLevel(sourceDefinition.trustLevel())
                            .build();
                    documents.add(document);
                    log.debug("加载可信知识库文档: sourceId={}, file={}", sourceDefinition.sourceId(), resource.getFilename());
                } catch (Exception e) {
                    throw new IllegalStateException("加载可信知识文档失败: " + resource.getFilename(), e);
                }
            }
            return documents;
        } catch (Exception e) {
            throw new IllegalStateException("扫描可信知识库目录失败", e);
        }
    }

    private String firstHeading(String content) {
        for (String line : content.split("\\R")) {
            if (line.startsWith("# ")) return line.substring(2).trim();
        }
        return "未命名章节";
    }

    private String buildVersion(List<Document> documents) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            documents.stream().sorted(java.util.Comparator.comparing(Document::getSource)).forEach(document -> {
                digest.update(document.getSource().getBytes(StandardCharsets.UTF_8));
                digest.update(document.getSourceVersion().getBytes(StandardCharsets.UTF_8));
                digest.update(document.getText().getBytes(StandardCharsets.UTF_8));
            });
            return HexFormat.of().formatHex(digest.digest()).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException("无法生成知识库构建版本", e);
        }
    }
}
