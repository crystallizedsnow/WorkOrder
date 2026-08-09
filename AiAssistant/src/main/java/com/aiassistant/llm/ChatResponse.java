package com.aiassistant.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * LLM 聊天响应（格式化输出）。
 * <ul>
 *   <li>content：模型的自然语言文本</li>
 *   <li>toolCalls：模型发起的工具调用列表（结构化，无需正则解析）</li>
 *   <li>finishReason：stop / tool_calls / length 等</li>
 *   <li>promptTokens / completionTokens：Token 用量统计</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private String content;

    private List<ToolCall> toolCalls;

    private String finishReason;

    private Integer promptTokens;

    private Integer completionTokens;

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
