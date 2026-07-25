package com.aiassistant.hook;

public enum HookType {
    SESSION_START,
    SESSION_END,
    POST_LLM_RESPONSE,
    PRE_TOOL_USE,
    POST_TOOL_USE
}