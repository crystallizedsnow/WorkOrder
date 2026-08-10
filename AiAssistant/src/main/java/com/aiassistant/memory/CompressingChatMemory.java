package com.aiassistant.memory;

import com.aiassistant.llm.ChatMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 带压缩的聊天记忆包装器（替代 langchain4j 的 ChatMemory）。
 * 使用自定义 {@link ChatMessage}，读取时触发 {@link ContextCompressor} 压缩。
 */
@Slf4j
public class CompressingChatMemory {

    private final List<ChatMessage> messages = new ArrayList<>();
    private final ContextCompressor contextCompressor;

    public CompressingChatMemory(ContextCompressor contextCompressor) {
        this.contextCompressor = contextCompressor;
    }

    public void add(ChatMessage message) {
        messages.add(message);
    }

    public void addAll(List<ChatMessage> messagesToAdd) {
        messages.addAll(messagesToAdd);
    }

    public List<ChatMessage> messages() {
        return contextCompressor.compress(new ArrayList<>(messages));
    }

    public void clear() {
        messages.clear();
    }
}
