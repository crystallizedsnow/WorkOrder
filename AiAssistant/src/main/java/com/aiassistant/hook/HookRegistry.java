package com.aiassistant.hook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class HookRegistry {

    private final Map<HookType, List<AgentHook>> hooks = new ConcurrentHashMap<>();

    public HookRegistry() {
        for (HookType type : HookType.values()) {
            hooks.put(type, Collections.synchronizedList(new ArrayList<>()));
        }
    }

    public void registerHook(HookType type, AgentHook hook) {
        hooks.get(type).add(hook);
        log.debug("注册Hook: {} - {}", type, hook.getClass().getSimpleName());
    }

    public void unregisterHook(HookType type, AgentHook hook) {
        hooks.get(type).remove(hook);
    }

    public void executeHooks(HookType type, HookContext context) {
        List<AgentHook> hookList = hooks.get(type);
        for (AgentHook hook : hookList) {
            try {
                hook.execute(type, context);
            } catch (Exception e) {
                log.error("Hook执行失败: {} - {}", type, hook.getClass().getSimpleName(), e);
            }
        }
    }

    public List<AgentHook> getHooks(HookType type) {
        return Collections.unmodifiableList(hooks.get(type));
    }
}