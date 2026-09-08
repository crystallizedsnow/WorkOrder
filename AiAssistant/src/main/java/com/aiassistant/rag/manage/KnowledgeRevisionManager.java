package com.aiassistant.rag.manage;

import com.aiassistant.embedding.EmbeddingModel;
import com.aiassistant.rag.ActiveRevisionRegistry;
import com.aiassistant.rag.ContentRetriever;
import com.aiassistant.rag.Document;
import com.aiassistant.rag.DocumentSplitter;
import com.aiassistant.rag.TrustLevel;
import com.aiassistant.rag.VectorStore;
import com.aiassistant.rag.document.KnowledgeDocumentParser;
import com.aiassistant.rag.document.KnowledgeDocumentParserRegistry;
import com.aiassistant.rag.document.ParsedKnowledgeDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KnowledgeRevisionManager {
    private static final ZoneId VERSION_ZONE = ZoneId.of("Asia/Shanghai");
    private final KnowledgeDocumentParserRegistry parsers;
    private final DocumentSplitter splitter;
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final ContentRetriever retriever;
    private final ActiveRevisionRegistry activeRegistry;
    private final Path storageRoot;
    private final Duration confirmationTtl;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final Map<String, LogicalDocument> documents = new ConcurrentHashMap<>();
    private final Map<String, String> documentKeys = new ConcurrentHashMap<>();
    private final Map<String, Revision> revisions = new ConcurrentHashMap<>();
    private final Map<String, PendingCheck> checks = new ConcurrentHashMap<>();
    private final Map<String, EvaluationReport> evaluations = new ConcurrentHashMap<>();

    public KnowledgeRevisionManager(KnowledgeDocumentParserRegistry parsers, DocumentSplitter splitter,
                                    EmbeddingModel embeddingModel, VectorStore vectorStore,
                                    ContentRetriever retriever, ActiveRevisionRegistry activeRegistry,
                                    @Value("${knowledge-base.storage.root:./knowledge-storage}") String root,
                                    @Value("${knowledge-base.upload.confirmation-ttl:PT10M}") Duration confirmationTtl) {
        this.parsers = parsers;
        this.splitter = splitter;
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.retriever = retriever;
        this.activeRegistry = activeRegistry;
        this.storageRoot = Path.of(root).toAbsolutePath().normalize();
        this.confirmationTtl = confirmationTtl;
        loadCatalog();
    }

    public synchronized CheckResult check(String space, String fileName, String contentType, byte[] bytes,
                                          String requestedVersion, String owner, TrustLevel trust, String actor) {
        requireText(space, "知识空间"); requireText(fileName, "文件名"); requireText(owner, "责任人"); requireText(actor, "操作人");
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("上传文件不能为空");
        if (bytes.length > 20 * 1024 * 1024) throw new IllegalArgumentException("上传文件不能超过20MB");
        if (trust == null || !trust.citable()) throw new IllegalArgumentException("只能上传可引用的可信文档");
        KnowledgeDocumentParser parser = parsers.require(fileName, contentType);
        ParsedKnowledgeDocument parsed = parser.parse(new ByteArrayInputStream(bytes));
        String normalizedName = normalizeFileName(fileName);
        String key = space.trim().toLowerCase(Locale.ROOT) + "\n" + normalizedName;
        LogicalDocument existing = documentKeys.containsKey(key) ? documents.get(documentKeys.get(key)) : null;
        String contentHash = sha256(parsed.normalizedText().getBytes(StandardCharsets.UTF_8));
        String binaryHash = sha256(bytes);
        Revision active = existing == null || existing.activeRevisionId() == null ? null : revisions.get(existing.activeRevisionId());
        if (active != null && active.contentHash().equals(contentHash)) {
            return new CheckResult(null, existing.documentId(), normalizedName, "NO_CHANGE", false,
                    active.versionLabel(), active.versionLabel(), active.contentHash(), contentHash,
                    parsed.warnings(), null, null);
        }
        String documentId = existing == null ? "doc_" + sha256(key.getBytes(StandardCharsets.UTF_8)).substring(0, 20)
                : existing.documentId();
        String versionLabel = requestedVersion == null || requestedVersion.isBlank()
                ? nextVersionLabel(documentId) : requestedVersion.trim();
        Revision sameLabel = revisions.values().stream().filter(r -> r.documentId().equals(documentId)
                && r.versionLabel().equals(versionLabel)).findFirst().orElse(null);
        String result = existing == null ? "NEW_DOCUMENT" : "CONTENT_CHANGED";
        if (sameLabel != null) result = sameLabel.contentHash().equals(contentHash)
                ? "VERSION_ALREADY_EXISTS" : "VERSION_LABEL_CONFLICT";
        if ("VERSION_ALREADY_EXISTS".equals(result)) {
            return new CheckResult(null, documentId, normalizedName, result, false,
                    active == null ? null : active.versionLabel(), versionLabel,
                    active == null ? null : active.contentHash(), contentHash, parsed.warnings(), null, null);
        }
        String checkId = "chk_" + UUID.randomUUID();
        String token = UUID.randomUUID().toString();
        Instant expires = Instant.now().plus(confirmationTtl);
        checks.put(checkId, new PendingCheck(checkId, token, actor, space, documentId, normalizedName, fileName,
                owner, trust, parser.format(), parser.version(), versionLabel, contentHash, binaryHash,
                parsed, bytes.clone(), existing == null ? null : existing.activeRevisionId(), expires));
        return new CheckResult(checkId, documentId, normalizedName, result, true,
                active == null ? null : active.versionLabel(), versionLabel,
                active == null ? null : active.contentHash(), contentHash, parsed.warnings(), token, expires);
    }

    public synchronized Revision apply(String documentId, String checkId, String token, boolean confirmed, String actor,
                                       String expectedActiveRevisionId) {
        PendingCheck pending = requireCheck(checkId, token, actor);
        if (!pending.documentId().equals(documentId)) throw new IllegalArgumentException("检查任务与文档ID不匹配");
        if (!confirmed) {
            checks.remove(checkId);
            throw new IllegalArgumentException("用户已取消更新");
        }
        if (!java.util.Objects.equals(pending.expectedActiveRevisionId(), expectedActiveRevisionId)) {
            throw new IllegalStateException("请求中的预期活动版本与检查结果不一致");
        }
        LogicalDocument current = documents.get(pending.documentId());
        String actualActive = current == null ? null : current.activeRevisionId();
        if (!java.util.Objects.equals(actualActive, pending.expectedActiveRevisionId())) {
            throw new IllegalStateException("活动版本已经变化，请重新检查");
        }
        if (revisions.values().stream().anyMatch(r -> r.documentId().equals(pending.documentId())
                && r.versionLabel().equals(pending.versionLabel()) && !r.contentHash().equals(pending.contentHash()))) {
            throw new IllegalStateException("版本标签已被其他内容占用");
        }
        String revisionId = "rev_" + UUID.randomUUID();
        Document source = Document.builder().text(pending.parsed().normalizedText())
                .source(pending.documentId()).sourceName(pending.parsed().title())
                .sourceVersion(pending.versionLabel()).documentId(pending.documentId()).revisionId(revisionId)
                .format(pending.format()).headingPath(pending.parsed().title()).trustLevel(pending.trust()).build();
        List<Document> chunks = splitter.split(source);
        List<float[]> vectors = embeddingModel.embedAll(chunks.stream().map(Document::getText).toList());
        vectorStore.addRevision(vectors, chunks);
        Path stored = store(pending, revisionId);
        long versionNo = revisions.values().stream().filter(r -> r.documentId().equals(pending.documentId()))
                .mapToLong(Revision::versionNo).max().orElse(0) + 1;
        Revision revision = new Revision(revisionId, pending.documentId(), versionNo, pending.versionLabel(),
                pending.contentHash(), pending.binaryHash(), pending.format(), pending.parserVersion(),
                "structured-char-v1", chunks.size(), RevisionStatus.EVALUATING, stored.toString(), actor, Instant.now());
        revisions.put(revisionId, revision);
        if (current == null) {
            LogicalDocument created = new LogicalDocument(pending.documentId(), pending.space(), pending.normalizedFileName(),
                    pending.parsed().title(), null, revisionId, pending.owner(), pending.trust(), 0);
            documents.put(created.documentId(), created);
            documentKeys.put(pending.space().trim().toLowerCase(Locale.ROOT) + "\n" + pending.normalizedFileName(), created.documentId());
        } else {
            documents.put(current.documentId(), current.withLatest(revisionId));
        }
        checks.remove(checkId);
        saveCatalog();
        return revision;
    }

    public synchronized EvaluationReport evaluate(String documentId, String revisionId, List<EvaluationCase> cases) {
        Revision revision = requireRevision(documentId, revisionId);
        if (cases == null || cases.isEmpty()) throw new IllegalArgumentException("至少提供一条文档变更评测用例");
        Set<String> baseline = activeRegistry.snapshot().revisionIds();
        Set<String> candidate = activeRegistry.snapshot().replacing(documentId, revisionId).revisionIds();
        int baselineHits = 0, candidateHits = 0;
        List<String> failures = new ArrayList<>();
        boolean leak = false;
        String oldRevision = documents.containsKey(documentId) ? documents.get(documentId).activeRevisionId() : null;
        for (EvaluationCase item : cases) {
            requireText(item.question(), "评测问题"); requireText(item.expectedText(), "期望文本");
            if (contains(retriever.retrieve(item.question(), baseline), item.expectedText())) baselineHits++;
            List<Document> result = retriever.retrieve(item.question(), candidate);
            if (contains(result, item.expectedText())) candidateHits++; else failures.add(item.question());
            if (oldRevision != null && !oldRevision.equals(revisionId)
                    && result.stream().anyMatch(doc -> oldRevision.equals(doc.getRevisionId()))) leak = true;
        }
        double baselineRecall = (double) baselineHits / cases.size();
        double candidateRecall = (double) candidateHits / cases.size();
        boolean passed = candidateRecall >= 1.0 && !leak;
        String id = "eval_" + UUID.randomUUID();
        EvaluationReport report = new EvaluationReport(id, documentId, revisionId, cases.size(), baselineRecall,
                candidateRecall, leak, passed, List.copyOf(failures), Instant.now());
        evaluations.put(id, report);
        saveCatalog();
        return report;
    }

    public EvaluationReport evaluation(String id) {
        EvaluationReport report = evaluations.get(id);
        if (report == null) throw new IllegalArgumentException("评测记录不存在: " + id);
        return report;
    }

    public synchronized Revision activate(String evaluationId, String actor, String expectedActiveRevisionId) {
        EvaluationReport report = evaluation(evaluationId);
        if (!report.passed()) throw new IllegalStateException("候选版本未通过评测，禁止激活");
        Revision revision = requireRevision(report.documentId(), report.revisionId());
        LogicalDocument document = requireDocument(report.documentId());
        activeRegistry.activate(document.documentId(), revision.revisionId(), expectedActiveRevisionId);
        documents.put(document.documentId(), document.withActive(revision.revisionId()));
        revisions.put(revision.revisionId(), revision.withStatus(RevisionStatus.ACTIVE));
        if (expectedActiveRevisionId != null && revisions.containsKey(expectedActiveRevisionId)) {
            revisions.put(expectedActiveRevisionId, revisions.get(expectedActiveRevisionId).withStatus(RevisionStatus.INACTIVE));
        }
        saveCatalog();
        return revisions.get(revision.revisionId());
    }

    public synchronized RollbackCheck rollbackCheck(String documentId, String revisionId, String actor) {
        requireText(actor, "操作人"); requireRevision(documentId, revisionId);
        LogicalDocument document = requireDocument(documentId);
        String token = UUID.randomUUID().toString();
        String checkId = "rollback_" + UUID.randomUUID();
        Instant expires = Instant.now().plus(confirmationTtl);
        checks.put(checkId, PendingCheck.rollback(checkId, token, actor, document, revisionId, expires));
        return new RollbackCheck(checkId, token, document.activeRevisionId(), revisionId, expires);
    }

    public synchronized Revision rollback(String checkId, String token, String actor, String expectedActiveRevisionId) {
        PendingCheck pending = requireCheck(checkId, token, actor);
        if (!pending.rollback()) throw new IllegalArgumentException("确认令牌不是回退操作");
        Revision target = requireRevision(pending.documentId(), pending.rollbackRevisionId());
        LogicalDocument document = requireDocument(pending.documentId());
        activeRegistry.activate(document.documentId(), target.revisionId(), expectedActiveRevisionId);
        documents.put(document.documentId(), document.withActive(target.revisionId()));
        if (expectedActiveRevisionId != null && revisions.containsKey(expectedActiveRevisionId)) {
            revisions.put(expectedActiveRevisionId, revisions.get(expectedActiveRevisionId).withStatus(RevisionStatus.INACTIVE));
        }
        revisions.put(target.revisionId(), target.withStatus(RevisionStatus.ACTIVE));
        checks.remove(checkId);
        saveCatalog();
        return revisions.get(target.revisionId());
    }

    public List<Revision> revisions(String documentId) {
        return revisions.values().stream().filter(r -> r.documentId().equals(documentId))
                .sorted(Comparator.comparingLong(Revision::versionNo).reversed()).toList();
    }

    /** Rehydrates immutable uploaded revisions during an explicit full index rebuild. */
    public synchronized List<Document> revisionDocuments() {
        List<Document> result = new ArrayList<>();
        for (Revision revision : revisions.values()) {
            LogicalDocument logical = documents.get(revision.documentId());
            if (logical == null) continue;
            try (var input = Files.newInputStream(Path.of(revision.storagePath()))) {
                KnowledgeDocumentParser parser = parsers.require(logical.normalizedFileName(), null);
                ParsedKnowledgeDocument parsed = parser.parse(input);
                result.add(Document.builder().text(parsed.normalizedText()).source(revision.documentId())
                        .sourceName(parsed.title()).sourceVersion(revision.versionLabel())
                        .documentId(revision.documentId()).revisionId(revision.revisionId())
                        .format(revision.format()).headingPath(parsed.title()).trustLevel(logical.trust()).build());
            } catch (Exception e) {
                throw new IllegalStateException("历史知识版本恢复失败: " + revision.revisionId(), e);
            }
        }
        return result;
    }

    private PendingCheck requireCheck(String id, String token, String actor) {
        PendingCheck value = checks.get(id);
        if (value == null) throw new IllegalArgumentException("检查任务不存在或已经使用");
        if (value.expiresAt().isBefore(Instant.now())) { checks.remove(id); throw new IllegalArgumentException("确认令牌已过期"); }
        if (!value.token().equals(token) || !value.actor().equals(actor)) throw new SecurityException("确认令牌无效");
        return value;
    }

    private Revision requireRevision(String documentId, String revisionId) {
        Revision value = revisions.get(revisionId);
        if (value == null || !value.documentId().equals(documentId)) throw new IllegalArgumentException("文档版本不存在");
        return value;
    }
    private LogicalDocument requireDocument(String id) {
        LogicalDocument value = documents.get(id);
        if (value == null) throw new IllegalArgumentException("文档不存在: " + id);
        return value;
    }
    private boolean contains(List<Document> docs, String expected) {
        String needle = expected.toLowerCase(Locale.ROOT);
        return docs.stream().anyMatch(doc -> doc.getText() != null && doc.getText().toLowerCase(Locale.ROOT).contains(needle));
    }
    private Path store(PendingCheck pending, String revisionId) {
        try {
            Path dir = storageRoot.resolve(pending.space()).resolve(pending.documentId()).normalize();
            if (!dir.startsWith(storageRoot)) throw new IllegalArgumentException("非法知识空间路径");
            Files.createDirectories(dir);
            String suffix = pending.format().equals("DOCX") ? ".docx" : ".md";
            Path temp = Files.createTempFile(dir, revisionId, ".tmp");
            Files.write(temp, pending.bytes());
            Path target = dir.resolve(revisionId + suffix);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (Exception e) { throw new IllegalStateException("知识原文件保存失败", e); }
    }
    private String nextVersionLabel(String documentId) {
        String date = LocalDate.now(VERSION_ZONE).format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
        long count = revisions.values().stream().filter(r -> r.documentId().equals(documentId)
                && r.versionLabel().startsWith(date + ".")).count();
        return date + "." + (count + 1);
    }
    private String normalizeFileName(String value) {
        String normalized = Normalizer.normalize(Path.of(value).getFileName().toString().trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("文件名无效");
        return normalized;
    }
    private String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private void requireText(String value, String field) { if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "不能为空"); }

    private void loadCatalog() {
        Path path = storageRoot.resolve("catalog.json");
        if (!Files.exists(path)) return;
        try {
            Catalog catalog = json.readValue(path.toFile(), Catalog.class);
            if (catalog.documents() != null) documents.putAll(catalog.documents());
            if (catalog.documentKeys() != null) documentKeys.putAll(catalog.documentKeys());
            if (catalog.revisions() != null) revisions.putAll(catalog.revisions());
            if (catalog.evaluations() != null) evaluations.putAll(catalog.evaluations());
            documents.values().stream().filter(doc -> doc.activeRevisionId() != null)
                    .forEach(doc -> activeRegistry.seed(doc.documentId(), doc.activeRevisionId()));
        } catch (Exception e) {
            throw new IllegalStateException("知识版本目录加载失败: " + path, e);
        }
    }

    private void saveCatalog() {
        try {
            Files.createDirectories(storageRoot);
            Path target = storageRoot.resolve("catalog.json");
            Path temp = Files.createTempFile(storageRoot, "catalog-", ".tmp");
            json.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(),
                    new Catalog(Map.copyOf(documents), Map.copyOf(documentKeys), Map.copyOf(revisions), Map.copyOf(evaluations)));
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new IllegalStateException("知识版本目录保存失败", e);
        }
    }

    public enum RevisionStatus { EVALUATING, ACTIVE, INACTIVE, FAILED }
    public record CheckResult(String checkId, String documentId, String normalizedFileName, String result,
                              boolean requiresConfirmation, String currentVersion, String proposedVersion,
                              String currentHash, String proposedHash, List<String> warnings,
                              String confirmationToken, Instant expiresAt) {}
    public record EvaluationCase(String question, String expectedText) {}
    public record EvaluationReport(String evaluationId, String documentId, String revisionId, int total,
                                   double baselineRecall, double candidateRecall, boolean oldVersionLeak,
                                   boolean passed, List<String> failures, Instant createdAt) {}
    public record Revision(String revisionId, String documentId, long versionNo, String versionLabel,
                           String contentHash, String binaryHash, String format, String parserVersion,
                           String chunkingVersion, int chunkCount, RevisionStatus status, String storagePath,
                           String createdBy, Instant createdAt) {
        Revision withStatus(RevisionStatus value) { return new Revision(revisionId, documentId, versionNo, versionLabel,
                contentHash, binaryHash, format, parserVersion, chunkingVersion, chunkCount, value, storagePath, createdBy, createdAt); }
    }
    public record LogicalDocument(String documentId, String space, String normalizedFileName, String displayName,
                                   String activeRevisionId, String latestRevisionId, String owner,
                                   TrustLevel trust, long lockVersion) {
        LogicalDocument withLatest(String revision) { return new LogicalDocument(documentId, space, normalizedFileName,
                displayName, activeRevisionId, revision, owner, trust, lockVersion + 1); }
        LogicalDocument withActive(String revision) { return new LogicalDocument(documentId, space, normalizedFileName,
                displayName, revision, latestRevisionId, owner, trust, lockVersion + 1); }
    }
    private record PendingCheck(String checkId, String token, String actor, String space, String documentId,
                                String normalizedFileName, String originalFileName, String owner, TrustLevel trust,
                                String format, String parserVersion, String versionLabel, String contentHash,
                                String binaryHash, ParsedKnowledgeDocument parsed, byte[] bytes,
                                String expectedActiveRevisionId, Instant expiresAt, boolean rollback,
                                String rollbackRevisionId) {
        PendingCheck(String checkId, String token, String actor, String space, String documentId,
                     String normalizedFileName, String originalFileName, String owner, TrustLevel trust,
                     String format, String parserVersion, String versionLabel, String contentHash,
                     String binaryHash, ParsedKnowledgeDocument parsed, byte[] bytes,
                     String expectedActiveRevisionId, Instant expiresAt) {
            this(checkId, token, actor, space, documentId, normalizedFileName, originalFileName, owner, trust,
                    format, parserVersion, versionLabel, contentHash, binaryHash, parsed, bytes,
                    expectedActiveRevisionId, expiresAt, false, null);
        }
        static PendingCheck rollback(String id, String token, String actor, LogicalDocument document,
                                     String target, Instant expires) {
            return new PendingCheck(id, token, actor, document.space(), document.documentId(),
                    document.normalizedFileName(), null, document.owner(), document.trust(), null, null,
                    null, null, null, null, null, document.activeRevisionId(), expires, true, target);
        }
    }
    public record RollbackCheck(String checkId, String confirmationToken, String currentRevisionId,
                                String targetRevisionId, Instant expiresAt) {}
    public record Catalog(Map<String, LogicalDocument> documents, Map<String, String> documentKeys,
                          Map<String, Revision> revisions, Map<String, EvaluationReport> evaluations) {}
}
