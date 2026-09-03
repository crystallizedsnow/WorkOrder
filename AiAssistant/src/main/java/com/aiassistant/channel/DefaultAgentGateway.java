package com.aiassistant.channel;

import com.aiassistant.agent.AgentLoop;
import com.aiassistant.channel.model.AgentRequest;
import com.aiassistant.intent.IntentShadowService;
import com.aiassistant.channel.confirmation.WriteConfirmationCoordinator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
public class DefaultAgentGateway implements AgentGateway {
    private final AgentLoop agentLoop;
    private final SessionExecutionCoordinator coordinator;
    private final IntentShadowService intentShadowService;
    private final WriteConfirmationCoordinator confirmations;

    @Override
    public Flux<String> execute(AgentRequest request) {
        return Flux.defer(() -> {
            return Flux.fromIterable(coordinator.execute(request.sessionId(), () -> {
                var handled = confirmations.handle(request);
                if (handled.isPresent()) return java.util.List.of(handled.get());
                intentShadowService.observe(request);
                return agentLoop.run(request).collectList().block();
            }));
        });
    }
}
