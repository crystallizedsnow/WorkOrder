package com.aiassistant.memory;

import com.aiassistant.llm.ChatMessage;
import com.aiassistant.llm.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MessageUnitSegmenterTest {
    @Test void keepsToolCallAndResultInOneCompleteUnit() {
        ToolCall call = ToolCall.builder().id("call-1").type("function")
                .function(ToolCall.FunctionCall.builder().name("query").arguments("{}").build()).build();
        List<SessionMemoryDocument.StoredMessage> messages = List.of(
                stored(1, ChatMessage.user("查询")),
                stored(2, ChatMessage.assistantWithToolCalls("", List.of(call))),
                stored(3, ChatMessage.tool("call-1", "query", "ok")),
                stored(4, ChatMessage.assistant("完成")));
        List<MessageUnit> units = new MessageUnitSegmenter().segment(messages);
        assertEquals(1, units.size());
        assertTrue(units.get(0).complete());
        assertEquals(4, units.get(0).messages().size());
    }

    @Test void marksMissingToolResultIncomplete() {
        ToolCall call = ToolCall.builder().id("call-1").type("function")
                .function(ToolCall.FunctionCall.builder().name("query").arguments("{}").build()).build();
        List<MessageUnit> units = new MessageUnitSegmenter().segment(List.of(
                stored(1, ChatMessage.user("查询")), stored(2, ChatMessage.assistantWithToolCalls("", List.of(call)))));
        assertFalse(units.get(0).complete());
    }

    private SessionMemoryDocument.StoredMessage stored(long sequence, ChatMessage message) {
        return SessionMemoryDocument.StoredMessage.builder().sequence(sequence).message(message).build();
    }
}
