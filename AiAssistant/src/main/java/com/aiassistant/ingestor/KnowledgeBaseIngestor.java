package com.aiassistant.ingestor;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class KnowledgeBaseIngestor {

    @Value("${knowledge-base.directory:classpath:knowledge-base/}")
    private String knowledgeBaseDirectory;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    @Qualifier("localEmbeddingModel")
    private EmbeddingModel embeddingModel;

    @Autowired
    private ResourcePatternResolver resourcePatternResolver;

    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationEvent(ContextRefreshedEvent event) {
        new Thread(() -> {
            try {
                List<Document> documents = loadDocumentsFromClasspath();
                if (documents.isEmpty()) {
                    log.warn("未找到知识库文档");
                    return;
                }

                EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                        .embeddingModel(embeddingModel)
                        .embeddingStore(embeddingStore)
                        .build();

                ingestor.ingest(documents);
                log.info("知识库文档导入完成，共导入 {} 个文档", documents.size());
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
                    java.nio.file.Path filePath = java.nio.file.Paths.get(resource.getURI());
                    if (java.nio.file.Files.exists(filePath)) {
                        List<Document> loadedDocuments = FileSystemDocumentLoader.loadDocuments(filePath.getParent());
                        documents.addAll(loadedDocuments);
                        log.debug("加载知识库文档: {}", resource.getFilename());
                    }
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