package com.aiassistant.channel;

import com.aiassistant.agent.AgentLoop;
import com.aiassistant.channel.model.AgentRequest;
import com.aiassistant.intent.IntentShadowService;
import com.aiassistant.intent.IntentRoutingProperties;
import com.aiassistant.intent.IntentShadowRouter;
import com.aiassistant.intent.RagRoutingPolicy;
import com.aiassistant.intent.RoutingDecision;
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
    private final IntentShadowRouter intentRouter;
    private final IntentRoutingProperties intentProperties;
    private final RagRoutingPolicy ragRoutingPolicy;
    private final WriteConfirmationCoordinator confirmations;

    @Override
    public Flux<String> execute(AgentRequest request) {
        return Flux.defer(() -> {
            return Flux.fromIterable(coordinator.execute(request.sessionId(), () -> {
                var handled = confirmations.handle(request);
                if (handled.isPresent()) return java.util.List.of(handled.get());
                intentShadowService.observe(request);
                if (!intentProperties.isEnabled() || intentProperties.getMode() != IntentRoutingProperties.Mode.ENFORCE) {
                    return agentLoop.run(request).collectList().block();
                }
                RoutingDecision decision;
                try {
                    decision = intentRouter.route(request);
                } catch (RuntimeException error) {
                    return java.util.List.of("暂时无法判断该请求是否需要知识库，请稍后重试或更具体地描述问题。");
                }
                if (decision.routeType() == com.aiassistant.intent.RouteType.OUT_OF_SCOPE) {
                    return java.util.List.of("这个请求不属于当前工单系统支持的范围。你可以询问工单流程、状态、权限，或执行已支持的工单操作。");
                }
                if (decision.routeType() == com.aiassistant.intent.RouteType.CLARIFY) {
                    String missing = decision.missingInformation().isEmpty() ? "请补充要处理的对象和期望操作。"
                            : "请补充以下信息：" + String.join("、", decision.missingInformation());
                    return java.util.List.of(missing);
                }
                return agentLoop.run(request, ragRoutingPolicy.decide(decision)).collectList().block();
            }));
        });
    }
}
