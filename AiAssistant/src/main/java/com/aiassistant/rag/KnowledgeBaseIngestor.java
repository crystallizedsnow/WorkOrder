package com.aiassistant.rag;

import com.aiassistant.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库导入器：启动时加载 classpath 下的 Markdown 文档，切分、向量化后写入向量存储。
 * <p>
 * 替代原 com.aiassistant.ingestor.KnowledgeBaseIngestor（不再依赖 langchain4j EmbeddingStoreIngestor）。
 * 当向量化模型或向量存储不可用时自动跳过，保证应用可启动。
 */
@Component
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

    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationEvent(ContextRefreshedEvent event) {
        new Thread(() -> {
            try {
                if (embeddingModel == null || !embeddingModel.isAvailable()) {
                    log.warn("向量化模型不可用，跳过知识库导入");
                    return;
                }
                if (vectorStore == null || !vectorStore.isAvailable()) {
                    log.warn("向量存储不可用，跳过知识库导入");
                    return;
                }

                List<Document> documents = loadDocumentsFromClasspath();
                if (documents.isEmpty()) {
                    log.warn("未找到知识库文档");
                    return;
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
                vectorStore.addAll(vectors, segments);
                log.info("知识库文档导入完成，共导入 {} 个片段", segments.size());
            } catch (Exception e) {
                log.error("知识库文档导入失败", e);
            }
        }, "knowledge-base-ingestor").start();
    }

    private List<Document> loadDocumentsFromClasspath() {
        List<Document> documents = new ArrayList<>();
        try {
            String pattern = knowledgeBaseDirectory + "**/*.md";
            Resource[] resources = resourcePatternResolver.getResources(pattern);
            for (Resource resource : resources) {
                try {
                    String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    String source = resource.getFilename();
                    documents.add(Document.of(content, source));
                    log.debug("加载知识库文档: {}", source);
                } catch (Exception e) {
                    log.warn("加载文档失败: {}", resource.getFilename(), e);
                }
            }
        } catch (Exception e) {
            log.error("扫描知识库目录失败", e);
        }
        return documents;
    }
}
