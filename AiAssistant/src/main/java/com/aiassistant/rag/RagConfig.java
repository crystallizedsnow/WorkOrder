package com.aiassistant.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.aiassistant.embedding.EmbeddingModel;
import com.aiassistant.llm.LlmConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * RAG 配置：构建 ES 客户端、向量存储、内容检索器。
 * 替代原 AgentConfig（不再依赖 langchain4j 的 ElasticsearchEmbeddingStore / EmbeddingStoreContentRetriever）。
 */
@Configuration
@ConditionalOnProperty(prefix = "llm.embedding", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class RagConfig {

    @Value("${vector-store.elasticsearch.uris:http://localhost:9200}")
    private String esUri;

    @Value("${vector-store.elasticsearch.alias-name:workorder-kb-current}")
    private String aliasName;

    @Value("${vector-store.elasticsearch.index-prefix:workorder-kb-v}")
    private String indexPrefix;

    @Value("${vector-store.elasticsearch.connect-timeout-ms:10000}")
    private int connectTimeoutMs;

    @Value("${vector-store.elasticsearch.socket-timeout-ms:60000}")
    private int socketTimeoutMs;

    @Value("${vector-store.rag.max-results:5}")
    private int maxResults;

    @Value("${vector-store.rag.min-score:0.6}")
    private double minScore;

    @Value("${vector-store.rag.candidate-results:20}")
    private int candidateResults;

    @Bean(destroyMethod = "close")
    public RestClient elasticsearchRestClient() {
        HttpHost host = HttpHost.create(esUri);
        return RestClient.builder(host)
                .setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                        .setConnectTimeout(connectTimeoutMs)
                        .setSocketTimeout(socketTimeoutMs)
                        .setConnectionRequestTimeout(connectTimeoutMs))
                .build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }

    @Bean
    public VectorStore vectorStore(ElasticsearchClient elasticsearchClient, LlmConfig llmConfig) {
        int dimension = llmConfig.getEmbedding().getDimension();
        return new ElasticsearchVectorStore(elasticsearchClient, aliasName, indexPrefix, dimension);
    }

    @Bean
    public ContentRetriever contentRetriever(EmbeddingModel embeddingModel, VectorStore vectorStore,
                                             ObjectProvider<MeterRegistry> meterRegistry) {
        return new EmbeddingStoreContentRetriever(embeddingModel, vectorStore, maxResults,
                candidateResults, minScore, meterRegistry.getIfAvailable());
    }
}
