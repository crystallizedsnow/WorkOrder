package com.aiassistant.channel;

import com.aiassistant.agent.AgentLoop;
import com.aiassistant.channel.model.AgentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
public class DefaultAgentGateway implements AgentGateway {
    private final AgentLoop agentLoop;

    @Override
    public Flux<String> execute(AgentRequest request) {
        return agentLoop.run(request);
    }
}
