package com.aiassistant.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.update_aliases.Action;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Elasticsearch 向量存储：稳定 ID、Bulk 写入和版本化 Alias 原子发布。 */
@Slf4j
public class ElasticsearchVectorStore implements VectorStore {

    private final ElasticsearchClient client;
    private final String aliasName;
    private final String indexPrefix;
    private final int dimension;
    private final boolean available;

    public ElasticsearchVectorStore(ElasticsearchClient client, String aliasName, String indexPrefix, int dimension) {
        this.client = client;
        this.aliasName = aliasName;
        this.indexPrefix = indexPrefix;
        this.dimension = dimension;
        this.available = ping();
    }

    /** 兼容旧构造方式。 */
    public ElasticsearchVectorStore(ElasticsearchClient client, String aliasName, int dimension) {
        this(client, aliasName, aliasName + "-v", dimension);
    }

    private boolean ping() {
        try {
            return client.ping().value();
        } catch (Exception e) {
            log.warn("ES 不可用，RAG 将降级: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void publishAll(List<float[]> vectors, List<Document> documents, String buildVersion) {
        requireValidBatch(vectors, documents);
        String physicalIndex = indexPrefix + sanitize(buildVersion) + "-" + Instant.now().toEpochMilli();
        boolean created = false;
        try {
            createIndex(physicalIndex);
            created = true;
            BulkResponse response = client.bulk(b -> {
                b.refresh(Refresh.WaitFor);
                for (int i = 0; i < documents.size(); i++) {
                    Document document = documents.get(i);
                    Map<String, Object> body = toSource(vectors.get(i), document, buildVersion);
                    b.operations(op -> op.index(idx -> idx.index(physicalIndex).id(document.getId()).document(body)));
                }
                return b;
            });
            if (response.errors()) {
                String firstError = response.items().stream().filter(item -> item.error() != null)
                        .map(item -> item.id() + ": " + item.error().reason()).findFirst().orElse("unknown bulk error");
                throw new IllegalStateException("知识向量 Bulk 写入失败: " + firstError);
            }
            long count = client.count(c -> c.index(physicalIndex)).count();
            if (count != documents.size()) {
                throw new IllegalStateException("知识索引数量校验失败: expected=" + documents.size() + ", actual=" + count);
            }
            switchAlias(physicalIndex);
            cleanupOldIndices(physicalIndex, 2);
            log.info("RAG 索引发布成功: alias={}, index={}, chunks={}, version={}",
                    aliasName, physicalIndex, count, buildVersion);
        } catch (Exception e) {
            if (created) deleteQuietly(physicalIndex);
            throw new IllegalStateException("RAG 索引构建失败，旧版本保持不变", e);
        }
    }

    @Override
    public void addAll(List<float[]> vectors, List<Document> documents) {
        publishAll(vectors, documents, "manual");
    }

    @Override
    public void add(float[] vector, Document document) {
        publishAll(List.of(vector), List.of(document), "manual");
    }

    private void requireValidBatch(List<float[]> vectors, List<Document> documents) {
        if (!available) throw new IllegalStateException("Elasticsearch 不可用");
        if (vectors == null || documents == null || vectors.isEmpty() || vectors.size() != documents.size()) {
            throw new IllegalArgumentException("向量与知识片段必须非空且一一对应");
        }
        for (int i = 0; i < vectors.size(); i++) {
            if (vectors.get(i) == null || vectors.get(i).length != dimension) {
                throw new IllegalArgumentException("向量维度不匹配: item=" + i + ", expected=" + dimension);
            }
            Document document = documents.get(i);
            if (document == null || document.getId() == null || document.getId().isBlank()) {
                throw new IllegalArgumentException("知识片段缺少稳定 ID: item=" + i);
            }
            if (document.getTrustLevel() == null || !document.getTrustLevel().citable()) {
                throw new IllegalArgumentException("非可信知识片段禁止入库: " + document.getId());
            }
        }
    }

    private void createIndex(String index) throws Exception {
        CreateIndexRequest request = CreateIndexRequest.of(b -> b.index(index)
                .mappings(TypeMapping.of(m -> m
                        .properties("embedding", Property.of(p -> p.denseVector(DenseVectorProperty.of(d -> d
                                .dims(dimension).index(true)))))
                        .properties("text", Property.of(p -> p.text(t -> t)))
                        .properties("source", Property.of(p -> p.keyword(k -> k)))
                        .properties("sourceName", Property.of(p -> p.text(t -> t)))
                        .properties("sourceVersion", Property.of(p -> p.keyword(k -> k)))
                        .properties("headingPath", Property.of(p -> p.text(t -> t)))
                        .properties("trustLevel", Property.of(p -> p.keyword(k -> k)))
                        .properties("buildVersion", Property.of(p -> p.keyword(k -> k))))));
        client.indices().create(request);
    }

    private Map<String, Object> toSource(float[] vector, Document document, String buildVersion) {
        Map<String, Object> body = new HashMap<>();
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) values.add(value);
        body.put("embedding", values);
        body.put("text", document.getText());
        body.put("source", value(document.getSource()));
        body.put("sourceName", value(document.getSourceName()));
        body.put("sourceVersion", value(document.getSourceVersion()));
        body.put("headingPath", value(document.getHeadingPath()));
        body.put("trustLevel", document.getTrustLevel().name());
        body.put("buildVersion", buildVersion);
        return body;
    }

    private void switchAlias(String newIndex) throws Exception {
        List<Action> actions = new ArrayList<>();
        if (client.indices().existsAlias(e -> e.name(aliasName)).value()) {
            var current = client.indices().getAlias(g -> g.name(aliasName));
            for (String index : current.result().keySet()) {
                actions.add(Action.of(a -> a.remove(r -> r.index(index).alias(aliasName).mustExist(true))));
            }
        }
        actions.add(Action.of(a -> a.add(add -> add.index(newIndex).alias(aliasName).isWriteIndex(true))));
        client.indices().updateAliases(u -> u.actions(actions));
    }

    @Override
    public List<Document> search(float[] queryVector, int maxResults, double minScore) {
        List<Document> results = new ArrayList<>();
        if (!available || queryVector == null || queryVector.length != dimension) return results;
        try {
            if (!client.indices().existsAlias(e -> e.name(aliasName)).value()) return results;
            List<Float> vector = new ArrayList<>(queryVector.length);
            for (float value : queryVector) vector.add(value);
            SearchResponse<Map> response = client.search(s -> s.index(aliasName).knn(k -> k.field("embedding")
                    .queryVector(vector).k((long) maxResults).numCandidates((long) Math.max(maxResults * 10, 50))), Map.class);
            for (Hit<Map> hit : response.hits().hits()) {
                if (hit.source() == null || hit.score() == null || hit.score() < minScore) continue;
                Map source = hit.source();
                TrustLevel trust = parseTrust(source.get("trustLevel"));
                if (!trust.citable()) continue;
                Document document = fromHit(hit, source, trust);
                document.getMetadata().put("score", hit.score());
                results.add(document);
            }
        } catch (Exception e) {
            log.warn("ES 向量检索失败: {}", e.getMessage());
        }
        return results;
    }

    @Override
    public List<Document> searchLexical(String query, int maxResults) {
        List<Document> results = new ArrayList<>();
        if (!available || query == null || query.isBlank()) return results;
        try {
            if (!client.indices().existsAlias(e -> e.name(aliasName)).value()) return results;
            SearchResponse<Map> response = client.search(s -> s.index(aliasName).size(maxResults)
                    .query(q -> q.multiMatch(m -> m.query(query).fields("text^2", "headingPath^3", "sourceName"))), Map.class);
            for (Hit<Map> hit : response.hits().hits()) {
                if (hit.source() == null) continue;
                Map source = hit.source();
                TrustLevel trust = parseTrust(source.get("trustLevel"));
                if (!trust.citable()) continue;
                Document document = fromHit(hit, source, trust);
                document.getMetadata().put("lexicalScore", hit.score() == null ? 0.0 : hit.score());
                results.add(document);
            }
        } catch (Exception e) {
            log.warn("ES 关键词检索失败: {}", e.getMessage());
        }
        return results;
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("版本化索引禁止原地清空，请发布新版本");
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public boolean hasPublishedIndex() {
        try { return available && client.indices().existsAlias(e -> e.name(aliasName)).value(); }
        catch (Exception e) { return false; }
    }

    @Override
    public boolean rollback() {
        if (!hasPublishedIndex()) return false;
        try {
            String current = client.indices().getAlias(g -> g.name(aliasName)).result().keySet().stream()
                    .findFirst().orElse(null);
            List<String> candidates = new ArrayList<>(client.indices().get(g -> g.index(indexPrefix + "*")).result().keySet());
            candidates.remove(current);
            candidates.sort((left, right) -> Long.compare(indexTimestamp(right), indexTimestamp(left)));
            if (candidates.isEmpty()) return false;
            switchAlias(candidates.get(0));
            log.warn("RAG 索引已回滚: from={}, to={}", current, candidates.get(0));
            return true;
        } catch (Exception e) {
            log.error("RAG 索引回滚失败", e);
            return false;
        }
    }

    private void deleteQuietly(String index) {
        try { client.indices().delete(d -> d.index(index)); }
        catch (Exception cleanupError) { log.warn("清理失败索引失败: index={}, error={}", index, cleanupError.getMessage()); }
    }

    private void cleanupOldIndices(String currentIndex, int retain) {
        try {
            List<String> indices = new ArrayList<>(client.indices().get(g -> g.index(indexPrefix + "*")).result().keySet());
            indices.sort((left, right) -> Long.compare(indexTimestamp(right), indexTimestamp(left)));
            int kept = 0;
            for (String index : indices) {
                if (index.equals(currentIndex) || kept < retain) {
                    kept++;
                    continue;
                }
                client.indices().delete(d -> d.index(index));
                log.info("已清理旧 RAG 索引: {}", index);
            }
        } catch (Exception e) {
            // 发布已经成功，清理失败不能回滚当前 Alias。
            log.warn("清理旧 RAG 索引失败: {}", e.getMessage());
        }
    }

    private long indexTimestamp(String index) {
        try { return Long.parseLong(index.substring(index.lastIndexOf('-') + 1)); }
        catch (Exception e) { return 0L; }
    }

    private String sanitize(String version) {
        String value = version == null ? "unknown" : version.toLowerCase().replaceAll("[^a-z0-9_-]", "-");
        return value.isBlank() ? "unknown" : value;
    }

    private TrustLevel parseTrust(Object value) {
        try { return TrustLevel.valueOf(string(value)); }
        catch (Exception e) { return TrustLevel.DRAFT; }
    }

    private Document fromHit(Hit<Map> hit, Map source, TrustLevel trust) {
        return Document.builder().id(hit.id()).text(string(source.get("text"))).source(string(source.get("source")))
                .sourceName(string(source.get("sourceName"))).sourceVersion(string(source.get("sourceVersion")))
                .headingPath(string(source.get("headingPath"))).trustLevel(trust).build();
    }

    private String string(Object value) { return value == null ? "" : value.toString(); }
    private String value(String value) { return value == null ? "" : value; }
}
