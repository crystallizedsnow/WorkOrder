package com.aiassistant.hook;

public interface AgentHook {
    void execute(HookType hookType, HookContext context);
}