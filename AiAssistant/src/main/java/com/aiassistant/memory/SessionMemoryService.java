package com.aiassistant.memory;

import com.aiassistant.channel.model.AgentRequest;
import com.aiassistant.llm.ChatMessage;
import com.aiassistant.llm.ToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionMemoryService {
    private final SessionMemoryRepository repository;
    private final TokenEstimator tokens;
    private final MessageUnitSegmenter segmenter;
    private final RollingSummaryCompressor compressor;

    @Value("${workorder.short-term-memory.context-window:16384}") private int contextWindow;
    @Value("${workorder.short-term-memory.max-output-tokens:4096}") private int outputReserve;
    @Value("${workorder.short-term-memory.safety-margin:1024}") private int safetyMargin;
    @Value("${workorder.short-term-memory.compression-trigger:12288}") private int compressionTrigger;
    @Value("${workorder.short-term-memory.recent-history-target:4600}") private int recentTarget;
    @Value("${workorder.short-term-memory.summary-max-tokens:2000}") private int summaryMax;
    @Value("${workorder.short-term-memory.ttl:P30D}") private Duration ttl;
    @Value("${workorder.short-term-memory.max-save-retries:3}") private int maxSaveRetries;

    public MemoryContext prepare(AgentRequest request, String systemPrompt, String rag, String currentUser,
                                 List<ToolDefinition> tools) {
        validateBudget();
        SessionMemoryDocument document = loadOrCreate(request);
        document = compressIfRequired(document, systemPrompt, rag, currentUser, tools);
        List<ChatMessage> result = new ArrayList<>();
        result.add(ChatMessage.system(systemPrompt));
        if (document.getSummary() != null && !document.getSummary().isBlank()) {
            result.add(ChatMessage.system("[历史会话摘要：仅作历史线索，实时业务状态和写操作授权必须重新验证]\n" + document.getSummary()));
        }
        if (rag != null && !rag.isBlank()) result.add(ChatMessage.system("[知识库信息]\n" + rag));
        List<SessionMemoryDocument.StoredMessage> safeRecent = segmenter.segment(document.getRecentMessages()).stream()
                .filter(MessageUnit::complete).flatMap(unit -> unit.messages().stream()).toList();
        safeRecent.stream().map(SessionMemoryDocument.StoredMessage::getMessage).filter(this::isPersistent).forEach(result::add);
        int baseCount = (int) result.stream().filter(message -> !"system".equals(message.getRole())).count();
        result.add(ChatMessage.user(currentUser));
        refreshExpiry(document);
        document = repository.save(document, document.getVersion());
        return new MemoryContext(result, document.getVersion(), baseCount);
    }

    public void save(AgentRequest request, MemoryContext context, List<ChatMessage> runtimeMessages, String state) {
        List<ChatMessage> stable = runtimeMessages.stream().filter(this::isPersistent).toList();
        int deltaStart = Math.min(context.baseRecentMessageCount(), stable.size());
        List<ChatMessage> delta = stable.subList(deltaStart, stable.size());
        String requestId = request.traceId();
        RuntimeException last = null;
        for (int attempt = 0; attempt < maxSaveRetries; attempt++) {
            SessionMemoryDocument latest = loadOrCreate(request);
            if (requestId != null && requestId.equals(latest.getLastRequestId())) return;
            long expected = latest.getVersion();
            long nextSequence = latest.getNextSequence();
            for (ChatMessage message : delta) latest.getRecentMessages().add(SessionMemoryDocument.StoredMessage.builder()
                    .sequence(nextSequence++).message(message).build());
            latest.setNextSequence(nextSequence);
            latest.setState(state == null ? "ACTIVE" : state);
            latest.setLastRequestId(requestId);
            refreshExpiry(latest);
            try { repository.save(latest, expected); return; }
            catch (MemoryVersionConflictException conflict) { last = conflict; }
        }
        throw last == null ? new IllegalStateException("Unable to save session memory") : last;
    }

    public void clear(AgentRequest request) { repository.delete(request.sessionId(), request.userId()); }

    /** 只为检索改写提供最近用户文本，不创建会话、不刷新 TTL。 */
    public List<String> recentUserMessages(AgentRequest request, int limit) {
        if (request == null || request.sessionId() == null || limit <= 0) return List.of();
        return repository.find(request.sessionId()).filter(document -> document.getWorkorderUserId() == null
                        || document.getWorkorderUserId().equals(request.userId()))
                .map(document -> document.getRecentMessages().stream()
                        .map(SessionMemoryDocument.StoredMessage::getMessage)
                        .filter(message -> message != null && "user".equals(message.getRole()))
                        .map(ChatMessage::getContent).filter(content -> content != null && !content.isBlank())
                        .skip(Math.max(0, document.getRecentMessages().stream()
                                .filter(item -> item.getMessage() != null && "user".equals(item.getMessage().getRole())).count() - limit))
                        .toList())
                .orElseGet(List::of);
    }

    private SessionMemoryDocument compressIfRequired(SessionMemoryDocument document, String systemPrompt, String rag,
                                                       String currentUser, List<ToolDefinition> tools) {
        List<ChatMessage> recent = document.getRecentMessages().stream().map(SessionMemoryDocument.StoredMessage::getMessage).toList();
        int recentTokens = tokens.messages(recent);
        int estimated = tokens.text(systemPrompt) + tokens.text(rag) + tokens.text(document.getSummary())
                + recentTokens + tokens.text(currentUser) + tokens.tools(tools) + outputReserve + safetyMargin;
        log.info("MEMORY-BUDGET sessionId={} estimatedTokens={} triggerTokens={} recentTokens={} recentMessages={} "
                        + "outputReserve={} safetyMargin={}",
                document.getSessionId(), estimated, compressionTrigger, recentTokens, recent.size(), outputReserve, safetyMargin);
        if (estimated < compressionTrigger) {
            log.info("MEMORY-COMPRESSION-SKIPPED sessionId={} reason=below_trigger estimatedTokens={} triggerTokens={}",
                    document.getSessionId(), estimated, compressionTrigger);
            return document;
        }
        if (recent.isEmpty()) {
            log.info("MEMORY-COMPRESSION-SKIPPED sessionId={} reason=no_recent_history", document.getSessionId());
            return document;
        }
        List<MessageUnit> units = segmenter.segment(document.getRecentMessages());
        List<MessageUnit> selected = new ArrayList<>();
        int remaining = recentTokens;
        for (MessageUnit unit : units) {
            // 当前用户消息尚未进入持久化历史，因此可以把唯一的上一轮完整单元滚入摘要。
            // 只保护不完整工具链，不再强制保留一个可能已经远超预算的历史单元。
            if (!unit.complete() || remaining <= recentTarget) break;
            selected.add(unit);
            remaining -= tokens.messages(unit.chatMessages());
        }
        if (selected.isEmpty()) {
            log.info("MEMORY-COMPRESSION-SKIPPED sessionId={} reason=no_eligible_complete_unit unitCount={} "
                            + "recentTokens={} recentTargetTokens={}",
                    document.getSessionId(), units.size(), recentTokens, recentTarget);
            return document;
        }
        try {
            long fromSequence = selected.get(0).messages().get(0).getSequence();
            long through = selected.get(selected.size() - 1).lastSequence();
            log.info("MEMORY-COMPRESSION-START sessionId={} selectedUnits={} sequenceFrom={} sequenceThrough={} "
                            + "recentTokensBefore={} recentTokensTarget={}",
                    document.getSessionId(), selected.size(), fromSequence, through, recentTokens, recentTarget);
            String summary = compressor.compress(document.getSummary(), selected);
            int summaryTokens = tokens.text(summary);
            if (summaryTokens > summaryMax) throw new IllegalStateException("Summary exceeds configured budget");
            SessionMemoryDocument updated = copy(document);
            updated.setSummary(summary); updated.setSummaryThroughSequence(through);
            updated.setRecentMessages(new ArrayList<>(updated.getRecentMessages().stream().filter(item -> item.getSequence() > through).toList()));
            updated.setLastCompressedAt(Instant.now()); refreshExpiry(updated);
            SessionMemoryDocument saved = repository.save(updated, document.getVersion());
            int recentTokensAfter = tokens.messages(saved.getRecentMessages().stream()
                    .map(SessionMemoryDocument.StoredMessage::getMessage).toList());
            log.info("MEMORY-COMPRESSION-SUCCESS sessionId={} summaryThroughSequence={} summaryTokens={} "
                            + "recentTokensBefore={} recentTokensAfter={} retainedMessages={}",
                    document.getSessionId(), through, summaryTokens, recentTokens, recentTokensAfter,
                    saved.getRecentMessages().size());
            return saved;
        } catch (Exception error) {
            log.warn("MEMORY-COMPRESSION-FAILED sessionId={} action=keep_original_history error={}",
                    document.getSessionId(), error.getMessage(), error);
            return document;
        }
    }

    private SessionMemoryDocument loadOrCreate(AgentRequest request) {
        SessionMemoryDocument value = repository.find(request.sessionId())
                .orElseGet(() -> repository.create(request.sessionId(), request.userId(), Instant.now().plus(ttl)));
        if (value.getWorkorderUserId() != null && !value.getWorkorderUserId().equals(request.userId()))
            throw new SecurityException("Session does not belong to current user");
        if (value.getExpiresAt() != null && !value.getExpiresAt().isAfter(Instant.now())) {
            repository.delete(request.sessionId(), request.userId());
            return repository.create(request.sessionId(), request.userId(), Instant.now().plus(ttl));
        }
        if (value.getRecentMessages() == null) value.setRecentMessages(new ArrayList<>());
        return value;
    }

    private void refreshExpiry(SessionMemoryDocument value) {
        value.setUpdatedAt(Instant.now()); value.setExpiresAt(Instant.now().plus(ttl));
    }

    private void validateBudget() {
        if (contextWindow <= 0 || outputReserve + safetyMargin >= contextWindow || compressionTrigger > contextWindow
                || recentTarget <= 0 || summaryMax <= 0) throw new IllegalStateException("Invalid short-term memory budget configuration");
    }

    private boolean isPersistent(ChatMessage message) {
        if (message == null || "system".equals(message.getRole())) return false;
        String content = message.getContent();
        if (content != null) {
            if (content.startsWith("[系统提醒]") || content.startsWith("[系统门禁]") || content.startsWith("[系统安全约束]")) return false;
            String normalized = content.toLowerCase();
            if (normalized.contains("bearer ") || normalized.contains("--password") || normalized.contains("\"password\"")
                    || normalized.contains("账号密码") || normalized.contains("手机号和密码")) return false;
        }
        return true;
    }

    private SessionMemoryDocument copy(SessionMemoryDocument source) {
        return SessionMemoryDocument.builder().id(source.getId()).sessionId(source.getSessionId())
                .workorderUserId(source.getWorkorderUserId()).summary(source.getSummary()).summaryVersion(source.getSummaryVersion())
                .summaryThroughSequence(source.getSummaryThroughSequence()).recentMessages(new ArrayList<>(source.getRecentMessages()))
                .nextSequence(source.getNextSequence()).version(source.getVersion()).state(source.getState())
                .lastRequestId(source.getLastRequestId()).createdAt(source.getCreatedAt()).updatedAt(source.getUpdatedAt())
                .expiresAt(source.getExpiresAt()).lastCompressedAt(source.getLastCompressedAt()).build();
    }
}
