package com.aiassistant.controller;

import com.aiassistant.channel.AgentGateway;
import com.aiassistant.channel.AuthenticatedUserResolver;
import com.aiassistant.memory.SessionMemoryService;
import com.aiassistant.channel.model.AgentRequest;
import com.aiassistant.channel.model.ChannelType;
import com.aiassistant.common.ChatForm;
import com.aiassistant.util.LogUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/assistant")
public class AIChatController {

    @Autowired
    private AgentGateway agentGateway;

    @Autowired
    private AuthenticatedUserResolver authenticatedUsers;

    @Autowired
    private SessionMemoryService sessionMemoryService;

    @GetMapping("/")
    public String healthCheck() {
        String traceId = MDC.get("traceId");
        LogUtils.entrance(traceId, "/assistant/", "GET", null);
        LogUtils.returnLog(traceId, "/assistant/", "GET", "success");
        return "AiAssistant is running";
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody ChatForm chatForm, @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ") || authHeader.length() == 7) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization must use Bearer scheme");
        }
        String traceId = MDC.get("traceId");
        Map<String, Object> params = new HashMap<>();
        params.put("memoryId", chatForm.getMemoryId());
        params.put("message", chatForm.getMessage());
        params.put("authHeader", authHeader != null ? "***" : null);
        LogUtils.entrance(traceId, "/assistant/chat", "POST", params);
        
        String token = authHeader.substring(7);
        String userId;
        try { userId = authenticatedUsers.resolve(token); }
        catch (SecurityException error) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, error.getMessage()); }
        catch (Exception error) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Token validation unavailable"); }
        return agentGateway.execute(new AgentRequest(chatForm.getMemoryId(), userId, chatForm.getMessage(),
                        token, ChannelType.WEB, null, null, "web:" + chatForm.getMemoryId(), traceId))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnComplete(() -> LogUtils.returnLog(traceId, "/assistant/chat", "POST", "completed"))
                .doOnError(e -> LogUtils.error(traceId, "/assistant/chat", params, e));
    }

    @DeleteMapping("/memory/{sessionId}")
    public void clearMemory(@PathVariable("sessionId") Long sessionId,
                            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ") || authHeader.length() == 7)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization must use Bearer scheme");
        String token = authHeader.substring(7);
        String userId = authenticatedUsers.resolve(token);
        sessionMemoryService.clear(new AgentRequest(sessionId, userId, "clear-memory", token,
                ChannelType.WEB, null, null, "web:" + sessionId, MDC.get("traceId")));
    }
}
