package com.aiassistant.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 基于 Elasticsearch 官方客户端的向量存储实现。
 * <p>
 * 替代 langchain4j-elasticsearch 的 ElasticsearchEmbeddingStore。
 * 索引结构：embedding(dense_vector, dims, cosine) + text(text) + source(text) + metadata(object)。
 */
@Slf4j
public class ElasticsearchVectorStore implements VectorStore {

    private final ElasticsearchClient client;
    private final String indexName;
    private final int dimension;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean available;

    public ElasticsearchVectorStore(ElasticsearchClient client, String indexName, int dimension) {
        this.client = client;
        this.indexName = indexName;
        this.dimension = dimension;
        this.available = ensureIndex();
    }

    private boolean ensureIndex() {
        try {
            boolean exists = client.indices().exists(e -> e.index(indexName)).value();
            if (!exists) {
                CreateIndexRequest request = CreateIndexRequest.of(b -> b
                        .index(indexName)
                        .mappings(TypeMapping.of(m -> m
                                .properties("embedding", Property.of(p -> p
                                        .denseVector(DenseVectorProperty.of(d -> d
                                                .dims(dimension)
                                                .index(true)))))
                                .properties("text", Property.of(p -> p.text(t -> t)))
                                .properties("source", Property.of(p -> p.text(t -> t))))));
                client.indices().create(request);
                log.info("ES 向量索引已创建: {}, dims={}", indexName, dimension);
            }
            return true;
        } catch (Exception e) {
            log.warn("ES 索引初始化失败，向量存储不可用: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void addAll(List<float[]> vectors, List<Document> documents) {
        if (!available || vectors == null || documents == null) {
            return;
        }
        int size = Math.min(vectors.size(), documents.size());
        for (int i = 0; i < size; i++) {
            add(vectors.get(i), documents.get(i));
        }
    }

    @Override
    public void add(float[] vector, Document document) {
        if (!available || vector == null || vector.length == 0 || document == null) {
            return;
        }
        try {
            Map<String, Object> doc = new HashMap<>();
            List<Float> vec = new ArrayList<>(vector.length);
            for (float v : vector) {
                vec.add(v);
            }
            doc.put("embedding", vec);
            doc.put("text", document.getText());
            doc.put("source", document.getSource() == null ? "" : document.getSource());

            String id = UUID.randomUUID().toString();
            client.index(i -> i.index(indexName).id(id).document(doc));
        } catch (Exception e) {
            log.warn("ES 写入向量失败: {}", e.getMessage());
        }
    }

    @Override
    public List<Document> search(float[] queryVector, int maxResults, double minScore) {
        List<Document> results = new ArrayList<>();
        if (!available || queryVector == null || queryVector.length == 0) {
            return results;
        }
        try {
            List<Float> vec = new ArrayList<>(queryVector.length);
            for (float v : queryVector) {
                vec.add(v);
            }

            SearchResponse<Map> response = client.search(s -> s
                            .index(indexName)
                            .knn(k -> k
                                    .field("embedding")
                                    .queryVector(vec)
                                    .k((long) maxResults)
                                    .numCandidates((long) Math.max(maxResults * 10, 50))),
                    Map.class);

            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source == null) {
                    continue;
                }
                double score = hit.score() == null ? 0.0 : hit.score();
                // ES cosine KNN score 范围 [0,1]，(1+cosine)/2，直接作为相似度
                if (score < minScore) {
                    continue;
                }
                Object text = source.get("text");
                Object src = source.get("source");
                Document doc = Document.of(
                        text == null ? "" : text.toString(),
                        src == null ? null : src.toString());
                doc.getMetadata().put("score", score);
                results.add(doc);
            }
        } catch (Exception e) {
            log.warn("ES 向量检索失败: {}", e.getMessage());
        }
        return results;
    }

    @Override
    public void clear() {
        if (!available) {
            return;
        }
        try {
            client.deleteByQuery(d -> d.index(indexName).query(q -> q.matchAll(m -> m)));
            log.info("ES 向量索引已清空: {}", indexName);
        } catch (Exception e) {
            log.warn("清空 ES 索引失败: {}", e.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }
}
