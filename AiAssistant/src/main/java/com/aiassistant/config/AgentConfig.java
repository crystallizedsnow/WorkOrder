package com.aiassistant.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class AgentConfig {

    @Value("${vector-store.elasticsearch.index-name:document-embeddings}")
    private String indexName;

    @Value("${vector-store.elasticsearch.dimension:384}")
    private int dimension;

    @Bean(destroyMethod = "close")
    public RestClient elasticsearchRestClient() {
        RestClientBuilder builder = RestClient.builder(new HttpHost("localhost", 9200, "http"))
                .setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                        .setConnectTimeout(10000)
                        .setSocketTimeout(60000)
                        .setConnectionRequestTimeout(10000));
        return builder.build();
    }

    @Bean
    public EmbeddingStore<TextSegment> elasticsearchEmbeddingStore(RestClient restClient) {
        return ElasticsearchEmbeddingStore.builder()
                .indexName(indexName)
                .restClient(restClient)
                .dimension(dimension)
                .build();
    }

    @Bean
    public ContentRetriever contentRetriever() {
        try {
            EmbeddingStore<TextSegment> embeddingStore = elasticsearchEmbeddingStore(elasticsearchRestClient());
            EmbeddingModel embeddingModel = localEmbeddingModel();
            return EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .maxResults(5)
                    .minScore(0.6)
                    .build();
        } catch (Exception e) {
            log.warn("创建ContentRetriever失败，使用空实现: {}", e.getMessage());
            return query -> java.util.Collections.emptyList();
        }
    }

    private EmbeddingModel localEmbeddingModel() {
        return new dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel();
    }
}