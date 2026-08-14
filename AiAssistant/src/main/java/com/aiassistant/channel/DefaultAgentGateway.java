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
    private final SessionExecutionCoordinator coordinator;

    @Override
    public Flux<String> execute(AgentRequest request) {
        return Flux.defer(() -> Flux.fromIterable(coordinator.execute(request.sessionId(),
                () -> agentLoop.run(request).collectList().block())));
    }
}
