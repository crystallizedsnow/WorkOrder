package com.aiassistant.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ToolResultProtector {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> IMPORTANT = List.of("code", "success", "message", "total", "page", "pageNum", "pageSize", "id", "workOrderId", "orderCode");
    private static final Set<String> CONTAINERS = Set.of("data", "records", "rows", "items", "list", "content", "result");
    private static final Set<String> RECORD_FIELDS = Set.of(
            "id", "code", "workOrderId", "workOrderCode", "orderCode", "title", "description",
            "priorityLevel", "priorityLevelDesc", "priority", "status", "statusDesc",
            "createTime", "createdAt", "createUserName", "creatorName", "assignedUserName",
            "handlerName", "auditUserName", "type", "typeDesc");
    private final TokenEstimator tokens;
    @Value("${workorder.short-term-memory.max-tool-result-tokens:3000}") private int maxTokens;

    public String protect(String value) {
        if (value == null || tokens.text(value) <= maxTokens) return value;
        ObjectNode protectedResult = MAPPER.createObjectNode();
        protectedResult.put("truncated", true);
        protectedResult.put("originalEstimatedTokens", tokens.text(value));
        protectedResult.put("retryable", false);
        protectedResult.put("notice", "工具结果已按关键业务字段精简；请直接使用 result 回答，不要重复执行相同命令。");
        try {
            JsonNode source = MAPPER.readTree(value);
            for (String field : IMPORTANT) {
                JsonNode found = find(source, field);
                if (found != null && (found.isValueNode() || found.size() <= 20)) protectedResult.set(field, found);
            }
            JsonNode nested = source.path("result");
            boolean structuredNested = !nested.isTextual();
            if (nested.isTextual()) {
                try { nested = MAPPER.readTree(nested.asText()); structuredNested = true; }
                catch (Exception ignored) { /* 非 JSON 文本走预览降级 */ }
            }
            if (structuredNested && !nested.isMissingNode() && !nested.isNull()) {
                protectedResult.set("result", compact(nested, false));
            }
        } catch (Exception ignored) {
            protectedResult.put("preview", value.substring(0, Math.min(value.length(), Math.max(200, maxTokens * 2))));
        }
        try { return MAPPER.writeValueAsString(protectedResult); }
        catch (Exception impossible) { return "{\"truncated\":true}"; }
    }

    private JsonNode compact(JsonNode node, boolean record) {
        if (node == null || node.isNull()) return MAPPER.nullNode();
        if (node.isArray()) {
            ArrayNode result = MAPPER.createArrayNode();
            for (int index = 0; index < Math.min(node.size(), 5); index++) result.add(compact(node.get(index), true));
            return result;
        }
        if (!node.isObject()) {
            if (node.isTextual() && node.asText().length() > 500) return MAPPER.getNodeFactory().textNode(node.asText().substring(0, 500));
            return node;
        }
        ObjectNode result = MAPPER.createObjectNode();
        node.fields().forEachRemaining(entry -> {
            String name = entry.getKey();
            JsonNode child = entry.getValue();
            if (child.isValueNode()) {
                if ((!record && IMPORTANT.contains(name)) || (record && RECORD_FIELDS.contains(name))) {
                    result.set(name, compact(child, record));
                }
            } else if (CONTAINERS.contains(name)) {
                result.set(name, compact(child, false));
            }
        });
        return result;
    }

    private JsonNode find(JsonNode node, String field) {
        if (node == null) return null;
        if (node.has(field)) return node.get(field);
        if (node.isObject()) for (JsonNode child : node) { JsonNode found = find(child, field); if (found != null) return found; }
        return null;
    }
}
