package com.aiassistant.config;

import com.aiassistant.memory.ContextCompressor;
import com.aiassistant.store.MongoChatMemoryStore;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@Slf4j
public class MemoryConfig {

    @Bean
    public ChatMemoryProvider chatMemoryProvider(MongoChatMemoryStore mongoChatMemoryStore, 
                                                  ContextCompressor contextCompressor) {
        return memoryId -> new ChatMemory() {
            private final Object id = memoryId;

            @Override
            public Object id() {
                return id;
            }

            @Override
            public void add(ChatMessage message) {
                List<ChatMessage> messages = mongoChatMemoryStore.getMessages(id);
                messages.add(message);
                mongoChatMemoryStore.updateMessages(id, messages);
            }

            @Override
            public List<ChatMessage> messages() {
                List<ChatMessage> messages = mongoChatMemoryStore.getMessages(id);
                return contextCompressor.compress(new ArrayList<>(messages));
            }

            @Override
            public void clear() {
                mongoChatMemoryStore.deleteMessages(id);
            }
        };
    }

    @Bean
    public ContextCompressor contextCompressor() {
        return new ContextCompressor();
    }
}