package com.aiassistant.memory;

import com.aiassistant.llm.ChatMessage;
import com.aiassistant.llm.ToolCall;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class MessageUnitSegmenter {
    public List<MessageUnit> segment(List<SessionMemoryDocument.StoredMessage> messages) {
        List<MessageUnit> units = new ArrayList<>();
        List<SessionMemoryDocument.StoredMessage> current = new ArrayList<>();
        Set<String> pending = new LinkedHashSet<>();
        for (SessionMemoryDocument.StoredMessage stored : messages == null ? List.<SessionMemoryDocument.StoredMessage>of() : messages) {
            ChatMessage message = stored.getMessage();
            if (message == null || "system".equals(message.getRole())) continue;
            if ("user".equals(message.getRole()) && !current.isEmpty() && pending.isEmpty()) {
                units.add(new MessageUnit(List.copyOf(current), true)); current.clear();
            }
            current.add(stored);
            if (message.getToolCalls() != null) for (ToolCall call : message.getToolCalls()) if (call.getId() != null) pending.add(call.getId());
            if ("tool".equals(message.getRole()) && message.getToolCallId() != null) pending.remove(message.getToolCallId());
            if ("assistant".equals(message.getRole()) && (message.getToolCalls() == null || message.getToolCalls().isEmpty()) && pending.isEmpty()) {
                units.add(new MessageUnit(List.copyOf(current), true)); current.clear();
            }
        }
        if (!current.isEmpty()) units.add(new MessageUnit(List.copyOf(current), pending.isEmpty()));
        return units;
    }
}
