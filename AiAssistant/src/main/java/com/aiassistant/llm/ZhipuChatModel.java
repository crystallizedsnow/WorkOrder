package com.aiassistant.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 智谱 GLM 聊天模型实现：直接调用 OpenAI 兼容的 HTTP API。
 * <p>
 * 不依赖 langchain4j，工具调用通过 function calling 协议结构化传递：
 * <ul>
 *   <li>输入：messages + tools 均为结构化 JSON</li>
 *   <li>输出：tool_calls 从响应 message.tool_calls 数组直接解析，无需正则</li>
 * </ul>
 */
@Component
@Slf4j
public class ZhipuChatModel implements ChatModel {

    private final RestClient restClient;
    private final LlmConfig llmConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public ZhipuChatModel(RestClient llmRestClient, LlmConfig llmConfig) {
        this.restClient = llmRestClient;
        this.llmConfig = llmConfig;
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools) {
        try {
            ObjectNode requestBody = buildRequestBody(messages, tools);

            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            return parseResponse(response);
        } catch (Exception e) {
            log.error("LLM HTTP 调用失败: {}", e.getMessage());
            throw new RuntimeException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    private ObjectNode buildRequestBody(List<ChatMessage> messages, List<ToolDefinition> tools) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", llmConfig.getChat().getModel());
        body.put("temperature", llmConfig.getChat().getTemperature());
        body.put("max_tokens", llmConfig.getChat().getMaxTokens());

        ArrayNode messagesNode = body.putArray("messages");
        for (ChatMessage message : messages) {
            messagesNode.add(buildMessageNode(message));
        }

        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsNode = body.putArray("tools");
            for (ToolDefinition tool : tools) {
                ObjectNode toolNode = toolsNode.addObject();
                toolNode.put("type", "function");
                ObjectNode functionNode = toolNode.putObject("function");
                functionNode.put("name", tool.getFunction().getName());
                functionNode.put("description", tool.getFunction().getDescription());
                if (tool.getFunction().getParameters() != null) {
                    functionNode.set("parameters", tool.getFunction().getParameters());
                }
            }
            body.put("tool_choice", "auto");
        }

        return body;
    }

    private ObjectNode buildMessageNode(ChatMessage message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", message.getRole());

        if (message.getContent() != null) {
            node.put("content", message.getContent());
        } else {
            // tool 角色消息 content 不能缺失，兜底空串
            node.put("content", "");
        }

        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            ArrayNode toolCallsNode = node.putArray("tool_calls");
            for (ToolCall toolCall : message.getToolCalls()) {
                ObjectNode callNode = toolCallsNode.addObject();
                callNode.put("id", toolCall.getId());
                callNode.put("type", "function");
                ObjectNode functionNode = callNode.putObject("function");
                functionNode.put("name", toolCall.getFunction().getName());
                functionNode.put("arguments", toolCall.getFunction().getArguments());
            }
        }

        if (message.getToolCallId() != null) {
            node.put("tool_call_id", message.getToolCallId());
        }

        if (message.getName() != null) {
            node.put("name", message.getName());
        }

        return node;
    }

    private ChatResponse parseResponse(JsonNode response) {
        if (response == null) {
            return ChatResponse.builder().content("").finishReason("empty").build();
        }

        JsonNode choices = response.path("choices");
        if (choices.isMissingNode() || !choices.isArray() || choices.isEmpty()) {
            String errorMsg = response.path("error").path("message").asText("");
            if (errorMsg.isEmpty()) {
                errorMsg = response.toString();
            }
            log.error("LLM 响应无 choices: {}", errorMsg);
            throw new RuntimeException("LLM 响应异常: " + errorMsg);
        }

        JsonNode firstChoice = choices.get(0);
        JsonNode message = firstChoice.path("message");
        String content = message.path("content").isMissingNode() ? "" : message.path("content").asText();
        String finishReason = firstChoice.path("finish_reason").asText("stop");

        List<ToolCall> toolCalls = new ArrayList<>();
        JsonNode toolCallsNode = message.path("tool_calls");
        if (toolCallsNode.isArray()) {
            for (JsonNode callNode : toolCallsNode) {
                ToolCall.FunctionCall function = ToolCall.FunctionCall.builder()
                        .name(callNode.path("function").path("name").asText())
                        .arguments(callNode.path("function").path("arguments").asText("{}"))
                        .build();
                toolCalls.add(ToolCall.builder()
                        .id(callNode.path("id").asText())
                        .type(callNode.path("type").asText("function"))
                        .function(function)
                        .build());
            }
        }

        JsonNode usage = response.path("usage");
        Integer promptTokens = usage.path("prompt_tokens").isMissingNode() ? null : usage.path("prompt_tokens").asInt();
        Integer completionTokens = usage.path("completion_tokens").isMissingNode() ? null : usage.path("completion_tokens").asInt();

        return ChatResponse.builder()
                .content(content)
                .toolCalls(toolCalls)
                .finishReason(finishReason)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .build();
    }
}
