package com.aiassistant.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具定义，作为 LLM function calling 协议的 tools 数组项输入。
 * parameters 为 JSON Schema（OpenAI/智谱兼容格式）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolDefinition {

    private String type;

    private FunctionSchema function;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FunctionSchema {
        private String name;
        private String description;
        private JsonNode parameters;
    }

    public static ToolDefinition of(String name, String description, JsonNode parameters) {
        return ToolDefinition.builder()
                .type("function")
                .function(FunctionSchema.builder()
                        .name(name)
                        .description(description)
                        .parameters(parameters)
                        .build())
                .build();
    }

    /**
     * 构造无参数的工具定义（parameters 为空 object）。
     */
    public static ToolDefinition ofNoArgs(String name, String description) {
        ObjectNode empty = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().createObjectNode();
        empty.put("type", "object");
        empty.putArray("properties");
        return of(name, description, empty);
    }
}
