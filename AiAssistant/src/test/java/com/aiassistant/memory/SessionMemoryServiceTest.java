package com.aiassistant.memory;

import com.aiassistant.channel.model.AgentRequest;
import com.aiassistant.channel.model.ChannelType;
import com.aiassistant.llm.ChatMessage;
import com.aiassistant.llm.ChatModel;
import com.aiassistant.llm.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SessionMemoryServiceTest {
    private InMemoryRepository repository;
    private SessionMemoryService service;

    @BeforeEach void setUp() {
        repository = new InMemoryRepository();
        ChatModel summaryModel = (messages, tools) -> ChatResponse.builder().content("当前目标：继续查询\n明确约束：无\n已完成事项：旧轮次").build();
        service = new SessionMemoryService(repository, new TokenEstimator(), new MessageUnitSegmenter(), new RollingSummaryCompressor(summaryModel));
        ReflectionTestUtils.setField(service, "contextWindow", 200);
        ReflectionTestUtils.setField(service, "outputReserve", 20);
        ReflectionTestUtils.setField(service, "safetyMargin", 10);
        ReflectionTestUtils.setField(service, "compressionTrigger", 90);
        ReflectionTestUtils.setField(service, "recentTarget", 25);
        ReflectionTestUtils.setField(service, "summaryMax", 80);
        ReflectionTestUtils.setField(service, "ttl", Duration.ofDays(30));
        ReflectionTestUtils.setField(service, "maxSaveRetries", 3);
    }

    @Test void savesOnlyStableConversationMessagesAndRefreshesTtl() {
        AgentRequest request = request("u1", "r1");
        MemoryContext context = service.prepare(request, "system", null, "hello", List.of());
        List<ChatMessage> runtime = new ArrayList<>(context.modelMessages());
        runtime.add(ChatMessage.assistant("world"));
        service.save(request, context, runtime, "ACTIVE");
        SessionMemoryDocument saved = repository.find(1L).orElseThrow();
        assertEquals(List.of("user", "assistant"), saved.getRecentMessages().stream().map(m -> m.getMessage().getRole()).toList());
        assertEquals(List.of(1L, 2L), saved.getRecentMessages().stream().map(SessionMemoryDocument.StoredMessage::getSequence).toList());
        assertTrue(saved.getExpiresAt().isAfter(Instant.now().plus(Duration.ofDays(29))));
    }

    @Test void rejectsDifferentOwner() {
        MemoryContext context = service.prepare(request("u1", "r1"), "system", null, "hello", List.of());
        assertNotNull(context);
        assertThrows(SecurityException.class, () -> service.prepare(request("u2", "r2"), "system", null, "hello", List.of()));
    }

    @Test void requestIdMakesSaveIdempotent() {
        AgentRequest request = request("u1", "same");
        MemoryContext context = service.prepare(request, "system", null, "hello", List.of());
        List<ChatMessage> runtime = new ArrayList<>(context.modelMessages()); runtime.add(ChatMessage.assistant("world"));
        service.save(request, context, runtime, "ACTIVE"); service.save(request, context, runtime, "ACTIVE");
        assertEquals(2, repository.find(1L).orElseThrow().getRecentMessages().size());
    }

    @Test void rollsOldCompleteUnitsIntoSummary() {
        for (int i = 0; i < 4; i++) {
            AgentRequest request = request("u1", "r" + i);
            MemoryContext context = service.prepare(request, "system", null, "继续查询第" + i + "轮", List.of());
            List<ChatMessage> runtime = new ArrayList<>(context.modelMessages());
            runtime.add(ChatMessage.assistant("这是第" + i + "轮的完整回答，包含足够内容用于触发预算压缩。"));
            service.save(request, context, runtime, "ACTIVE");
        }
        service.prepare(request("u1", "final"), "system", null, "继续", List.of());
        SessionMemoryDocument saved = repository.find(1L).orElseThrow();
        assertNotNull(saved.getSummary());
        assertTrue(saved.getSummaryThroughSequence() > 0);
        assertTrue(saved.getRecentMessages().size() < 8);
    }

    private AgentRequest request(String user, String trace) {
        return new AgentRequest(1L, user, "hello", "token", ChannelType.FEISHU, "tenant", "sender", "chat", trace);
    }

    private static class InMemoryRepository implements SessionMemoryRepository {
        private SessionMemoryDocument value;
        public Optional<SessionMemoryDocument> find(Long id) { return Optional.ofNullable(value); }
        public SessionMemoryDocument create(Long id, String user, Instant expires) {
            if (value == null) value = SessionMemoryDocument.builder().sessionId(id).workorderUserId(user).summaryVersion(1)
                    .recentMessages(new ArrayList<>()).nextSequence(1).version(0).state("ACTIVE")
                    .createdAt(Instant.now()).updatedAt(Instant.now()).expiresAt(expires).build();
            return value;
        }
        public SessionMemoryDocument save(SessionMemoryDocument document, long expected) {
            if (value != null && value.getVersion() != expected) throw new MemoryVersionConflictException(document.getSessionId());
            document.setVersion(expected + 1); value = document; return value;
        }
        public void delete(Long id, String user) { value = null; }
    }
}
