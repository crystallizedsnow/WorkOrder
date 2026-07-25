package com.aiassistant.hook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LoggingHook implements AgentHook {

    @Override
    public void execute(HookType hookType, HookContext context) {
        switch (hookType) {
            case SESSION_START:
                log.info("会话开始 - sessionId: {}, userId: {}", 
                        context.getSessionId(), context.getUserId());
                break;
            case SESSION_END:
                log.info("会话结束 - sessionId: {}, userId: {}", 
                        context.getSessionId(), context.getUserId());
                break;
            case POST_LLM_RESPONSE:
                log.debug("LLM响应 - sessionId: {}, tokens: {}, 响应长度: {}", 
                        context.getSessionId(), context.getTokenCount(),
                        context.getLlmResponse() != null ? context.getLlmResponse().length() : 0);
                break;
            case PRE_TOOL_USE:
                log.info("工具调用前 - sessionId: {}, toolName: {}", 
                        context.getSessionId(), context.getToolName());
                break;
            case POST_TOOL_USE:
                log.info("工具调用后 - sessionId: {}, toolName: {}, 结果长度: {}", 
                        context.getSessionId(), context.getToolName(),
                        context.getToolResult() != null ? context.getToolResult().length() : 0);
                break;
        }
    }
}