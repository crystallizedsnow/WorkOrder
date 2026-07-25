package com.aiassistant.hook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class TokenCountingHook implements AgentHook {

    private final ConcurrentHashMap<Long, AtomicInteger> sessionTokenCounts = new ConcurrentHashMap<>();

    @Override
    public void execute(HookType hookType, HookContext context) {
        if (context.getSessionId() == null) {
            return;
        }

        sessionTokenCounts.computeIfAbsent(context.getSessionId(), k -> new AtomicInteger(0));

        switch (hookType) {
            case POST_LLM_RESPONSE:
                int tokenCount = calculateTokenCount(context.getLlmResponse());
                int total = sessionTokenCounts.get(context.getSessionId()).addAndGet(tokenCount);
                log.debug("Token统计 - sessionId: {}, 本次: {}, 累计: {}", 
                        context.getSessionId(), tokenCount, total);
                break;
            case SESSION_END:
                int totalTokens = sessionTokenCounts.get(context.getSessionId()).get();
                log.info("会话Token统计 - sessionId: {}, 总Token数: {}", 
                        context.getSessionId(), totalTokens);
                sessionTokenCounts.remove(context.getSessionId());
                break;
        }
    }

    private int calculateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int asciiCount = 0;
        int nonAsciiCount = 0;
        for (char c : text.toCharArray()) {
            if (c < 128) {
                asciiCount++;
            } else {
                nonAsciiCount++;
            }
        }
        return (int) (asciiCount * 0.25 + nonAsciiCount);
    }

    public int getSessionTokenCount(Long sessionId) {
        return sessionTokenCounts.getOrDefault(sessionId, new AtomicInteger(0)).get();
    }
}