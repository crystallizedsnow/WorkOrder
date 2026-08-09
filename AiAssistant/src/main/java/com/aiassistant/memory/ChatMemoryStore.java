package com.aiassistant.memory;

import com.aiassistant.llm.ChatMessage;

import java.util.List;

/**
 * 聊天记忆存储接口（替代 dev.langchain4j.store.memory.chat.ChatMemoryStore）。
 * 使用自定义 {@link ChatMessage} 类型，序列化方式由实现决定。
 */
public interface ChatMemoryStore {

    List<ChatMessage> getMessages(Object memoryId);

    void updateMessages(Object memoryId, List<ChatMessage> messages);

    void deleteMessages(Object memoryId);
}
