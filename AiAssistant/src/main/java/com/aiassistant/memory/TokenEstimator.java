package com.aiassistant.memory;

import com.aiassistant.llm.ChatMessage;
import com.aiassistant.llm.ToolCall;
import com.aiassistant.llm.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TokenEstimator {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public int text(String value) {
        if (value == null || value.isEmpty()) return 0;
        int tokens = 0;
        int asciiRun = 0;
        for (int offset = 0; offset < value.length();) {
            int cp = value.codePointAt(offset);
            offset += Character.charCount(cp);
            if (cp <= 0x7f && Character.isLetterOrDigit(cp)) asciiRun++;
            else {
                tokens += (asciiRun + 2) / 3;
                asciiRun = 0;
                tokens += Character.isWhitespace(cp) ? 0 : 1;
            }
        }
        return tokens + (asciiRun + 2) / 3;
    }

    public int message(ChatMessage message) {
        if (message == null) return 0;
        int total = 6 + text(message.getRole()) + text(message.getContent()) + text(message.getToolCallId()) + text(message.getName());
        if (message.getToolCalls() != null) for (ToolCall call : message.getToolCalls()) {
            total += 8 + text(call.getId()) + text(call.getType());
            if (call.getFunction() != null) total += text(call.getFunction().getName()) + text(call.getFunction().getArguments());
        }
        return total;
    }

    public int messages(List<ChatMessage> messages) {
        return messages == null ? 0 : messages.stream().mapToInt(this::message).sum();
    }

    public int tools(List<ToolDefinition> tools) {
        try { return tools == null ? 0 : text(MAPPER.writeValueAsString(tools)) + tools.size() * 8; }
        catch (Exception ignored) { return tools == null ? 0 : tools.size() * 64; }
    }
}
