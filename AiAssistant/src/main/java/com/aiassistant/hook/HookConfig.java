package com.aiassistant.hook;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HookConfig {

    private final HookRegistry hookRegistry;
    private final LoggingHook loggingHook;
    private final TokenCountingHook tokenCountingHook;

    @PostConstruct
    public void registerBuiltinHooks() {
        hookRegistry.registerHook(HookType.SESSION_START, loggingHook);
        hookRegistry.registerHook(HookType.SESSION_END, loggingHook);
        hookRegistry.registerHook(HookType.SESSION_END, tokenCountingHook);
        hookRegistry.registerHook(HookType.POST_LLM_RESPONSE, loggingHook);
        hookRegistry.registerHook(HookType.POST_LLM_RESPONSE, tokenCountingHook);
        hookRegistry.registerHook(HookType.PRE_TOOL_USE, loggingHook);
        hookRegistry.registerHook(HookType.POST_TOOL_USE, loggingHook);
    }
}