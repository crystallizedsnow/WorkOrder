package com.aiassistant.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型返回的工具调用项（格式化输出）。
 * arguments 为 JSON 字符串，由调用方解析为具体参数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolCall {

    /** 工具调用唯一 ID，回传 tool 消息时使用 */
    private String id;

    /** 固定为 function */
    private String type;

    private FunctionCall function;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FunctionCall {
        @JsonProperty("name")
        private String name;

        @JsonProperty("arguments")
        private String arguments;
    }
}
