package com.aiassistant.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一的聊天消息类型，覆盖 OpenAI/智谱兼容协议的全部角色。
 * <p>
 * 角色约定：
 * <ul>
 *   <li>system / user / assistant：常规对话消息</li>
 *   <li>assistant + toolCalls：模型发起的工具调用请求（格式化输出）</li>
 *   <li>tool + toolCallId：工具执行结果回传给模型（格式化输入）</li>
 * </ul>
 * 替代 dev.langchain4j.data.message.* 系列消息类型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessage {

    private String role;

    private String content;

    /** assistant 角色发起的工具调用列表 */
    private List<ToolCall> toolCalls;

    /** tool 角色消息对应的工具调用 ID */
    private String toolCallId;

    /** tool 角色消息对应的工具名称 */
    private String name;

    public static ChatMessage system(String content) {
        return ChatMessage.builder().role("system").content(content).build();
    }

    public static ChatMessage user(String content) {
        return ChatMessage.builder().role("user").content(content).build();
    }

    public static ChatMessage assistant(String content) {
        return ChatMessage.builder().role("assistant").content(content).build();
    }

    public static ChatMessage assistantWithToolCalls(String content, List<ToolCall> toolCalls) {
        return ChatMessage.builder().role("assistant").content(content).toolCalls(toolCalls).build();
    }

    public static ChatMessage tool(String toolCallId, String name, String content) {
        return ChatMessage.builder().role("tool").toolCallId(toolCallId).name(name).content(content).build();
    }
}
