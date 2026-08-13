package com.aiassistant.channel;

import com.aiassistant.channel.model.*;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ChannelStageThreeFourTest {
    @Test void inboundModelIsImmutableAndValidated() {
        var attributes = new java.util.HashMap<String,String>(); attributes.put("chatType", "p2p");
        var message = message(attributes); attributes.put("changed", "yes");
        assertEquals(Map.of("chatType", "p2p"), message.attributes());
        assertThrows(IllegalArgumentException.class, () -> new InboundMessage(ChannelType.FEISHU, "bot", "t", "", "m", "u", "union", "x", "text", "trace", Instant.now(), Map.of()));
    }

    @Test void longRepliesAreSplitWithoutDataLoss() {
        String value = "12345\n67890\nabcde";
        var chunks = FeishuChannelAdapter.split(value, 7);
        assertEquals(value, String.join("", chunks));
        assertTrue(chunks.stream().allMatch(part -> part.length() <= 7));
    }

    @Test void rateLimiterRejectsRequestsBeyondWindowLimit() {
        ChannelRateLimiter limiter = new ChannelRateLimiter(2, Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC));
        assertTrue(limiter.allow("tenant:user")); assertTrue(limiter.allow("tenant:user")); assertFalse(limiter.allow("tenant:user"));
        assertTrue(limiter.allow("tenant:other"));
    }

    @Test void sameConversationExecutesSerially() throws Exception {
        ConversationExecutor executor = new ConversationExecutor(2);
        CountDownLatch done = new CountDownLatch(2); CopyOnWriteArrayList<Integer> order = new CopyOnWriteArrayList<>();
        executor.submit("same", () -> { try { Thread.sleep(60); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } order.add(1); done.countDown(); });
        executor.submit("same", () -> { order.add(2); done.countDown(); });
        assertTrue(done.await(2, TimeUnit.SECONDS)); assertEquals(java.util.List.of(1, 2), order); executor.close();
    }

    @Test void confirmationCardUsesOnlySupportedV2Layout() throws Exception {
        var confirmation = new com.aiassistant.channel.confirmation.WriteConfirmation();
        confirmation.setOperationId("op-1"); confirmation.setSummary("{\"tool\":\"executeCliCommand\",\"success\":true,\"result\":\"==========[dry-run] 执行计划预览==========  操作类型: 创建工单  目标端点: /api/execute\"}");
        confirmation.setExpiresAt(Instant.parse("2026-08-14T01:00:00Z"));
        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(FeishuChannelAdapter.buildConfirmationCard(confirmation));
        assertTrue(json.contains("\"schema\":\"2.0\""));
        assertTrue(json.contains("\"tag\":\"column_set\""));
        assertTrue(json.contains("\"element_id\":\"confirm_button\""));
        assertTrue(json.contains("\"element_id\":\"cancel_button\""));
        assertFalse(json.contains("\"tag\":\"action\""));
        assertTrue(json.contains("\"operationId\":\"op-1\""));
        assertTrue(json.contains("[dry-run] 执行计划预览"));
        assertFalse(json.contains("executeCliCommand"));
        assertFalse(json.contains("\\\"tool\\\""));
        assertTrue(json.contains("操作类型: 创建工单\\n目标端点: /api/execute"));
    }

    @Test void completedCardRemovesButtonsAndShowsFinalStatus() throws Exception {
        var confirmation = new com.aiassistant.channel.confirmation.WriteConfirmation();
        confirmation.setSummary("prefix [dry-run] 创建工单");
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                FeishuChannelAdapter.buildResultCard(confirmation,
                        com.aiassistant.channel.confirmation.WriteConfirmation.Status.SUCCEEDED));
        assertTrue(json.contains("工单操作已执行"));
        assertFalse(json.contains("confirm_button"));
        assertFalse(json.contains("cancel_button"));
    }

    private InboundMessage message(Map<String,String> attributes) {
        return new InboundMessage(ChannelType.FEISHU, "bot", "tenant", "chat", "message", "open", "union", "hello", "text", "trace", Instant.now(), attributes);
    }
}
