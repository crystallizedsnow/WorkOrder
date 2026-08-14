package com.aiassistant.rag;

import com.aiassistant.embedding.EmbeddingModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HybridContentRetrieverTest {
    @Test
    void rrfDeduplicatesAndCombinesBothChannels() {
        Document exact = document("exact", TrustLevel.REVIEWED);
        Document both = document("both", TrustLevel.REVIEWED);
        Document semantic = document("semantic", TrustLevel.REVIEWED);
        VectorStore store = new StubStore(List.of(exact, both), List.of(both, semantic));
        var retriever = new EmbeddingStoreContentRetriever(new StubEmbedding(), store, 5, 20, 0.1, null);

        List<Document> result = retriever.retrieve("600 状态");

        assertEquals(3, result.size());
        assertEquals("both", result.get(0).getId());
        assertNotNull(result.get(0).getMetadata().get("lexicalRank"));
        assertNotNull(result.get(0).getMetadata().get("semanticRank"));
    }

    @Test
    void authoritativeWinsWhenFusionScoresAreEqual() {
        Document reviewed = document("a", TrustLevel.REVIEWED);
        Document authoritative = document("b", TrustLevel.AUTHORITATIVE);
        var retriever = new EmbeddingStoreContentRetriever(new StubEmbedding(),
                new StubStore(List.of(reviewed), List.of(authoritative)), 2, 20, 0.1, null);
        assertEquals("b", retriever.retrieve("状态").get(0).getId());
    }

    private Document document(String id, TrustLevel trust) {
        return Document.builder().id(id).text(id).source(id).sourceName(id).sourceVersion("v1")
                .headingPath("章节").trustLevel(trust).build();
    }

    private static class StubEmbedding implements EmbeddingModel {
        public float[] embed(String text) { return new float[]{1}; }
        public List<float[]> embedAll(List<String> texts) { return List.of(); }
        public boolean isAvailable() { return true; }
        public int dimension() { return 1; }
    }

    private record StubStore(List<Document> lexical, List<Document> semantic) implements VectorStore {
        public void addAll(List<float[]> vectors, List<Document> documents) {}
        public void add(float[] vector, Document document) {}
        public List<Document> search(float[] vector, int max, double min) { return semantic; }
        public List<Document> searchLexical(String query, int max) { return lexical; }
        public void clear() {}
        public boolean isAvailable() { return true; }
    }
}
