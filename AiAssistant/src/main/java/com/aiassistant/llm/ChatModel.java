package com.aiassistant.llm;

import java.util.List;

/**
 * 聊天模型接口（直接调用 HTTP API，不依赖 langchain4j）。
 * <p>
 * 输入消息与工具定义均为结构化对象，输出为结构化 ChatResponse，
 * 工具调用通过 function calling 协议传递，无需正则解析。
 */
public interface ChatModel {

    /**
     * 同步聊天。
     *
     * @param messages 已格式化的消息列表
     * @param tools    可用工具定义列表，为空表示不启用 function calling
     * @return 结构化响应
     */
    ChatResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools);
}
