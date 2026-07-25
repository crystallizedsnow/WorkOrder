package com.aiassistant.controller;

import com.aiassistant.agent.AgentLoop;
import com.aiassistant.common.ChatForm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/assistant")
@Slf4j
public class AIChatController {

    @Autowired
    private AgentLoop agentLoop;

    @GetMapping("/")
    public String healthCheck() {
        return "AiAssistant is running";
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody ChatForm chatForm, @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        log.info("收到聊天请求, memoryId={}, message={}", chatForm.getMemoryId(), chatForm.getMessage());
        return agentLoop.run(chatForm.getMemoryId(), chatForm.getMessage(), authHeader)
                .subscribeOn(Schedulers.boundedElastic());
    }
}