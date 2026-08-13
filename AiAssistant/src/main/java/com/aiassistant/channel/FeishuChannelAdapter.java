package com.aiassistant.channel;

import com.aiassistant.channel.confirmation.WriteConfirmation;
import com.aiassistant.channel.model.ChannelType;
import com.aiassistant.channel.model.OutboundMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "workorder.channel.feishu.enabled", havingValue = "true")
public class FeishuChannelAdapter implements ChannelAdapter {
    private final FeishuProperties properties;
    private final Client client;
    private final ObjectMapper mapper = new ObjectMapper();

    public FeishuChannelAdapter(FeishuProperties properties) {
        this.properties = properties;
        this.client = Client.newBuilder(properties.getAppId(), properties.getAppSecret()).build();
    }

    public ChannelType type() { return ChannelType.FEISHU; }

    public void send(OutboundMessage message) {
        for (String part : split(message.text(), properties.getMaxMessageLength())) {
            sendChunk(message.conversationId(), part);
        }
    }

    public void sendConfirmation(WriteConfirmation confirmation) {
        sendCard(confirmation.getConversationId(), buildConfirmationCard(confirmation));
    }

    public void sendExecutionResult(WriteConfirmation confirmation, WriteConfirmation.Status status, String result) {
        String title = status == WriteConfirmation.Status.SUCCEEDED ? "✅ 工单操作执行成功" : "⚠️ 工单操作执行失败";
        String detail = extractResult(result);
        sendChunk(confirmation.getConversationId(), title + (detail.isBlank() ? "" : "\n\n" + detail));
    }

    static Map<String, Object> buildConfirmationCard(WriteConfirmation confirmation) {
        Map<String, Object> confirm = Map.of(
                "tag", "button", "element_id", "confirm_button",
                "text", Map.of("tag", "plain_text", "content", "确认执行"),
                "type", "primary", "width", "fill",
                "value", Map.of("action", "confirm", "operationId", confirmation.getOperationId()));
        Map<String, Object> cancel = Map.of(
                "tag", "button", "element_id", "cancel_button",
                "text", Map.of("tag", "plain_text", "content", "取消"),
                "type", "default", "width", "fill",
                "value", Map.of("action", "cancel", "operationId", confirmation.getOperationId()));
        Map<String, Object> columns = Map.of(
                "tag", "column_set", "element_id", "confirm_columns", "horizontal_spacing", "8px",
                "columns", List.of(
                        Map.of("tag", "column", "width", "weighted", "weight", 1, "elements", List.of(confirm)),
                        Map.of("tag", "column", "width", "weighted", "weight", 1, "elements", List.of(cancel))));
        String content = "**操作摘要**\n```text\n" + formatDryRun(confirmation.getSummary())
                + "\n```\n**有效期：** " + confirmation.getExpiresAt();
        return Map.of(
                "schema", "2.0", "config", Map.of("update_multi", true),
                "header", Map.of("title", Map.of("tag", "plain_text", "content", "请确认工单操作"), "template", "orange"),
                "body", Map.of("elements", List.of(
                        Map.of("tag", "markdown", "element_id", "summary_text", "content", content), columns)));
    }

    static Map<String, Object> buildResultCard(WriteConfirmation confirmation, WriteConfirmation.Status status) {
        boolean success = status == WriteConfirmation.Status.SUCCEEDED;
        String title = success ? "工单操作已执行" : status == WriteConfirmation.Status.CANCELLED ? "工单操作已取消" : "工单操作执行失败";
        String icon = success ? "✅" : status == WriteConfirmation.Status.CANCELLED ? "🚫" : "⚠️";
        return Map.of(
                "schema", "2.0", "config", Map.of("update_multi", true),
                "header", Map.of("title", Map.of("tag", "plain_text", "content", icon + " " + title),
                        "template", success ? "green" : status == WriteConfirmation.Status.CANCELLED ? "grey" : "red"),
                "body", Map.of("elements", List.of(Map.of("tag", "markdown", "content",
                        "**操作摘要**\n```text\n" + formatDryRun(confirmation.getSummary()) + "\n```"))));
    }

    static String formatDryRun(String raw) {
        if (raw == null) return "";
        String value = safe(raw);
        int start = value.indexOf("[dry-run]");
        if (start >= 0) value = value.substring(start);
        // CLI 在部分终端中丢失换行；根据分隔线和字段恢复卡片排版。
        value = value.replaceAll("={10,}", "\n==================================================\n")
                .replaceAll("-{10,}", "\n--------------------------------------------------\n")
                .replaceAll("\\s{2,}(?=(?:操作类型|目标端点|风险等级|预期影响|参数详情|注意):)", "\n")
                .replaceAll("\\s{4,}(?=[A-Za-z][A-Za-z0-9_-]*:)", "\n  ")
                .replaceAll("\\s{4,}(?=\\[[^]]+])", "  ")
                .replaceAll("\n{3,}", "\n\n").trim();
        return value;
    }

    private static String extractResult(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String value = raw;
        int result = value.indexOf("\"result\":");
        if (result >= 0) value = value.substring(result + 9).replaceAll("^[\\s\"]+|[\\s\"}]+$", "");
        return safe(value).replace("\\n", "\n");
    }

    private void sendCard(String conversationId, Map<String, Object> card) {
        try {
            String content = mapper.writeValueAsString(card);
            var body = CreateMessageReqBody.newBuilder().receiveId(conversationId).msgType("interactive").content(content).build();
            var request = CreateMessageReq.newBuilder().receiveIdType("chat_id").createMessageReqBody(body).build();
            var response = client.im().v1().message().create(request);
            if (!response.success()) throw new IllegalStateException("Feishu card API code=" + response.getCode());
        } catch (Exception e) {
            throw new IllegalStateException("Feishu card failed", e);
        }
    }

    private static String safe(String text) {
        String value = text.length() > 3000 ? text.substring(0, 3000) + "..." : text;
        return value.replaceAll("(?i)Bearer\\s+[A-Za-z0-9._-]+", "Bearer ***");
    }

    private void sendChunk(String chat, String text) {
        Exception last = null;
        for (int i = 0; i <= properties.getSendRetries(); i++) {
            try {
                String content = mapper.writeValueAsString(Map.of("text", text));
                var body = CreateMessageReqBody.newBuilder().receiveId(chat).msgType("text").content(content).build();
                var request = CreateMessageReq.newBuilder().receiveIdType("chat_id").createMessageReqBody(body).build();
                var response = client.im().v1().message().create(request);
                if (!response.success()) throw new IllegalStateException("Feishu API code=" + response.getCode());
                return;
            } catch (Exception e) {
                last = e;
                if (i < properties.getSendRetries()) try {
                    Thread.sleep(properties.getRetryDelay().toMillis());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }
        }
        throw new IllegalStateException("Feishu reply failed", last);
    }

    static List<String> split(String text, int max) {
        if (text.length() <= max) return List.of(text);
        List<String> output = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + max, text.length());
            if (end < text.length()) {
                int newline = text.lastIndexOf('\n', end);
                if (newline > start) end = newline + 1;
            }
            output.add(text.substring(start, end));
            start = end;
        }
        return output;
    }
}
