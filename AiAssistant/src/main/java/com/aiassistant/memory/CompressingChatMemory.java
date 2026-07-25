package com.aiassistant.memory;

import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

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