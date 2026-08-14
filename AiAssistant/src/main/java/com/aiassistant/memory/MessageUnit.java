package com.aiassistant.memory;

import com.aiassistant.llm.ChatMessage;
import java.util.List;

public record MessageUnit(List<SessionMemoryDocument.StoredMessage> messages, boolean complete) {
    public long lastSequence() { return messages.get(messages.size() - 1).getSequence(); }
    public List<ChatMessage> chatMessages() { return messages.stream().map(SessionMemoryDocument.StoredMessage::getMessage).toList(); }
}
