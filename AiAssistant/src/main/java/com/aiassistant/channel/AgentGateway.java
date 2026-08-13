package com.aiassistant.channel;

import com.aiassistant.channel.model.AgentRequest;
import reactor.core.publisher.Flux;

public interface AgentGateway {
    Flux<String> execute(AgentRequest request);
}
