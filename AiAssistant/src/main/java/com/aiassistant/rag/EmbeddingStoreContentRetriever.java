package com.aiassistant.rag;

import com.aiassistant.embedding.EmbeddingModel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** BM25 + KNN 双路召回，通过 RRF 融合、稳定去重并优先可信来源。 */
@Slf4j
public class EmbeddingStoreContentRetriever implements ContentRetriever {
    private static final int RRF_K = 60;

    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final int maxResults;
    private final int candidateResults;
    private final double minScore;
    private final MeterRegistry meterRegistry;

    public EmbeddingStoreContentRetriever(EmbeddingModel embeddingModel, VectorStore vectorStore,
                                          int maxResults, double minScore) {
        this(embeddingModel, vectorStore, maxResults, Math.max(20, maxResults * 4), minScore, null);
    }

    public EmbeddingStoreContentRetriever(EmbeddingModel embeddingModel, VectorStore vectorStore,
                                          int maxResults, int candidateResults, double minScore,
                                          MeterRegistry meterRegistry) {
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.maxResults = maxResults;
        this.candidateResults = candidateResults;
        this.minScore = minScore;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public List<Document> retrieve(String query) {
        return retrieve(query, null);
    }

    @Override
    public List<Document> retrieve(String query, Set<String> revisionIds) {
        if (query == null || query.isBlank() || embeddingModel == null || !embeddingModel.isAvailable()
                || vectorStore == null || !vectorStore.isAvailable()) return Collections.emptyList();
        if (revisionIds != null && revisionIds.isEmpty()) return Collections.emptyList();
        Timer.Sample sample = meterRegistry == null ? null : Timer.start(meterRegistry);
        try {
            List<Document> lexical = vectorStore.searchLexical(query, candidateResults, revisionIds);
            float[] vector = embeddingModel.embed(query);
            List<Document> semantic = vectorStore.search(vector, candidateResults, minScore, revisionIds);
            List<Document> result = reciprocalRankFusion(lexical, semantic, maxResults);
            if (meterRegistry != null) meterRegistry.counter("rag.retrieval.total", "result",
                    result.isEmpty() ? "empty" : "hit").increment();
            return result;
        } catch (Exception e) {
            log.warn("RAG 混合检索失败: {}", e.getMessage());
            if (meterRegistry != null) meterRegistry.counter("rag.retrieval.total", "result", "error").increment();
            return Collections.emptyList();
        } finally {
            if (sample != null) sample.stop(meterRegistry.timer("rag.retrieval.duration"));
        }
    }

    List<Document> reciprocalRankFusion(List<Document> lexical, List<Document> semantic, int limit) {
        Map<String, ScoredDocument> fused = new LinkedHashMap<>();
        addRanks(fused, lexical, "lexicalRank");
        addRanks(fused, semantic, "semanticRank");
        return fused.values().stream()
                .sorted(Comparator.comparingDouble(ScoredDocument::adjustedScore).reversed()
                        .thenComparing(item -> item.document().getId()))
                .limit(limit).map(item -> {
                    item.document().getMetadata().put("rrfScore", item.score());
                    return item.document();
                }).toList();
    }

    private void addRanks(Map<String, ScoredDocument> fused, List<Document> documents, String rankName) {
        if (documents == null) return;
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            if (document == null || document.getId() == null || document.getTrustLevel() == null
                    || !document.getTrustLevel().citable()) continue;
            double addition = 1.0 / (RRF_K + i + 1);
            int rank = i + 1;
            fused.compute(document.getId(), (id, current) -> {
                ScoredDocument value = current == null ? new ScoredDocument(document, 0.0) : current;
                value.document().getMetadata().put(rankName, rank);
                return new ScoredDocument(value.document(), value.score() + addition);
            });
        }
    }

    private record ScoredDocument(Document document, double score) {
        double adjustedScore() {
            return score + (document.getTrustLevel() == TrustLevel.AUTHORITATIVE ? 0.0001 : 0.0);
        }
    }
}
