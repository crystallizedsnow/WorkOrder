package com.aiassistant.rag.manage;

import com.aiassistant.embedding.EmbeddingModel;
import com.aiassistant.rag.ActiveRevisionRegistry;
import com.aiassistant.rag.ContentRetriever;
import com.aiassistant.rag.Document;
import com.aiassistant.rag.DocumentSplitter;
import com.aiassistant.rag.TrustLevel;
import com.aiassistant.rag.VectorStore;
import com.aiassistant.rag.document.KnowledgeDocumentParserRegistry;
import com.aiassistant.rag.document.MarkdownKnowledgeDocumentParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeRevisionManagerTest {
    @TempDir Path temp;

    @Test
    void incrementallyUpdatesEvaluatesActivatesAndRollsBackOneDocument() {
        MemoryStore store = new MemoryStore();
        ActiveRevisionRegistry active = new ActiveRevisionRegistry();
        ContentRetriever retriever = new MemoryRetriever(store);
        KnowledgeRevisionManager manager = new KnowledgeRevisionManager(
                new KnowledgeDocumentParserRegistry(List.of(new MarkdownKnowledgeDocumentParser())),
                new DocumentSplitter(), new StubEmbedding(), store, retriever, active,
                temp.toString(), Duration.ofMinutes(10));

        byte[] first = "# VPN 自助处理\n\n重新连接 VPN 后验证内网。".getBytes(StandardCharsets.UTF_8);
        var checked = manager.check("workorder", "guide.md", "text/markdown", first,
                "v1", "it", TrustLevel.REVIEWED, "admin");
        assertEquals("NEW_DOCUMENT", checked.result());
        var revision1 = manager.apply(checked.documentId(), checked.checkId(), checked.confirmationToken(), true, "admin", null);
        assertEquals(1, store.appendCalls);
        var evaluation1 = manager.evaluate(checked.documentId(), revision1.revisionId(),
                List.of(new KnowledgeRevisionManager.EvaluationCase("VPN怎么办", "重新连接 VPN")));
        assertTrue(evaluation1.passed());
        manager.activate(evaluation1.evaluationId(), "admin", null);

        var duplicate = manager.check("workorder", "GUIDE.md", "text/markdown", first,
                null, "it", TrustLevel.REVIEWED, "admin");
        assertEquals("NO_CHANGE", duplicate.result());
        assertFalse(duplicate.requiresConfirmation());

        byte[] second = "# VPN 自助处理\n\n退出客户端并重新连接 VPN，异常验证码必须创建工单。".getBytes(StandardCharsets.UTF_8);
        var changed = manager.check("workorder", "guide.md", "text/markdown", second,
                "v2", "it", TrustLevel.REVIEWED, "admin");
        assertEquals("CONTENT_CHANGED", changed.result());
        var revision2 = manager.apply(changed.documentId(), changed.checkId(), changed.confirmationToken(), true, "admin", revision1.revisionId());
        assertEquals(2, store.appendCalls);
        assertNotEquals(revision1.revisionId(), revision2.revisionId());
        var evaluation2 = manager.evaluate(changed.documentId(), revision2.revisionId(),
                List.of(new KnowledgeRevisionManager.EvaluationCase("异常验证码", "异常验证码必须创建工单")));
        assertTrue(evaluation2.passed());
        manager.activate(evaluation2.evaluationId(), "admin", revision1.revisionId());

        var rollback = manager.rollbackCheck(changed.documentId(), revision1.revisionId(), "admin");
        manager.rollback(rollback.checkId(), rollback.confirmationToken(), "admin", revision2.revisionId());
        assertEquals(revision1.revisionId(), active.snapshot().revisions().get(changed.documentId()));
        assertEquals(2, manager.revisions(changed.documentId()).size());
    }

    @Test
    void rejectsInvalidConfirmationAndVersionLabelCollision() {
        MemoryStore store = new MemoryStore();
        ActiveRevisionRegistry active = new ActiveRevisionRegistry();
        ContentRetriever retriever = new MemoryRetriever(store);
        KnowledgeRevisionManager manager = new KnowledgeRevisionManager(
                new KnowledgeDocumentParserRegistry(List.of(new MarkdownKnowledgeDocumentParser())),
                new DocumentSplitter(), new StubEmbedding(), store, retriever, active,
                temp.toString(), Duration.ofMinutes(10));
        var checked = manager.check("workorder", "same.md", "text/markdown",
                "# 标题\n\n第一版正文".getBytes(StandardCharsets.UTF_8), "v1", "it", TrustLevel.REVIEWED, "admin");
        assertThrows(SecurityException.class, () -> manager.apply(checked.documentId(), checked.checkId(), "wrong", true, "admin", null));
        var rev = manager.apply(checked.documentId(), checked.checkId(), checked.confirmationToken(), true, "admin", null);
        var report = manager.evaluate(checked.documentId(), rev.revisionId(),
                List.of(new KnowledgeRevisionManager.EvaluationCase("第一版", "第一版正文")));
        manager.activate(report.evaluationId(), "admin", null);
        var collision = manager.check("workorder", "same.md", "text/markdown",
                "# 标题\n\n不同正文".getBytes(StandardCharsets.UTF_8), "v1", "it", TrustLevel.REVIEWED, "admin");
        assertEquals("VERSION_LABEL_CONFLICT", collision.result());
    }

    private static class StubEmbedding implements EmbeddingModel {
        public float[] embed(String text) { return new float[]{1}; }
        public List<float[]> embedAll(List<String> texts) { return texts.stream().map(text -> new float[]{1}).toList(); }
        public boolean isAvailable() { return true; }
        public int dimension() { return 1; }
    }

    private static class MemoryRetriever implements ContentRetriever {
        private final MemoryStore store;
        private MemoryRetriever(MemoryStore store) { this.store = store; }
        public List<Document> retrieve(String query) { return store.documents; }
        public List<Document> retrieve(String query, Set<String> revisions) {
            if (revisions != null && revisions.isEmpty()) return List.of();
            return store.documents.stream().filter(doc -> revisions == null || revisions.contains(doc.getRevisionId())).toList();
        }
    }

    private static class MemoryStore implements VectorStore {
        private final List<Document> documents = new ArrayList<>();
        private int appendCalls;
        public void addAll(List<float[]> vectors, List<Document> documents) { this.documents.addAll(documents); }
        public void addRevision(List<float[]> vectors, List<Document> documents) { appendCalls++; this.documents.addAll(documents); }
        public void add(float[] vector, Document document) { documents.add(document); }
        public List<Document> search(float[] vector, int max, double min) { return documents; }
        public List<Document> search(float[] vector, int max, double min, Set<String> ids) {
            return documents.stream().filter(d -> ids.isEmpty() || ids.contains(d.getRevisionId())).limit(max).toList();
        }
        public List<Document> searchLexical(String query, int max) { return documents; }
        public void clear() { documents.clear(); }
        public boolean isAvailable() { return true; }
    }
}
