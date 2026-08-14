package com.aiassistant.memory;

import com.aiassistant.llm.ChatMessage;
import java.util.List;

public record MemoryContext(List<ChatMessage> modelMessages, long version, int baseRecentMessageCount) {}
