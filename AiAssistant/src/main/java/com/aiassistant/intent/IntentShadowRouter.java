package com.aiassistant.intent;

import com.aiassistant.channel.model.AgentRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class IntentShadowRouter {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final SemanticSkillRetriever retriever;
    private final IntentModelClient modelClient;
    private final IntentRoutingPolicy policy;
    private final IntentEvaluationHook hook;

    public IntentShadowRouter(SemanticSkillRetriever retriever, IntentModelClient modelClient,
                              IntentRoutingPolicy policy, IntentEvaluationHook hook) {
        this.retriever = retriever;
        this.modelClient = modelClient;
        this.policy = policy;
        this.hook = hook;
    }

    public RoutingDecision route(AgentRequest request) {
        long started = System.nanoTime();
        safeHook(() -> hook.onRequestStarted(request.query()));
        String stage = "control";
        try {
            if (isControlProtocol(request.query())) {
                RoutingDecision decision = policy.control("CONTROL_PROTOCOL_SHADOW", elapsedMs(started));
                safeHook(() -> hook.onPolicyCompleted(decision, 0));
                return decision;
            }
            stage = "retrieval";
            long phaseStarted = System.nanoTime();
            SemanticSkillRetriever.RetrievalResult retrieval = retriever.retrieve(request.query());
            long retrievalDurationMs = elapsedMs(phaseStarted);
            safeHook(() -> hook.onRetrievalCompleted(retrieval, retrievalDurationMs));
            stage = "model";
            phaseStarted = System.nanoTime();
            IntentModelClient.ModelProposal proposal = modelClient.classify(request.query(), retrieval);
            long modelDurationMs = elapsedMs(phaseStarted);
            safeHook(() -> hook.onModelCompleted(proposal, modelDurationMs));
            stage = "policy";
            phaseStarted = System.nanoTime();
            RoutingDecision decision = policy.decide(proposal, retrieval, elapsedMs(started));
            long policyDurationMs = elapsedMs(phaseStarted);
            safeHook(() -> hook.onPolicyCompleted(decision, policyDurationMs));
            return decision;
        } catch (Throwable error) {
            String failedStage = stage;
            safeHook(() -> hook.onFailed(failedStage, error, elapsedMs(started)));
            throw error;
        }
    }

    private boolean isControlProtocol(String query) {
        try {
            JsonNode node = MAPPER.readTree(query);
            if (!node.isObject() || !"last_dry_run".equals(node.path("target").asText())
                    || !node.has("confirmed") || !node.path("confirmed").isBoolean()) return false;
            String action = node.path("action").asText();
            return ("confirm_execute".equals(action) && node.path("confirmed").asBoolean())
                    || ("cancel_execute".equals(action) && !node.path("confirmed").asBoolean());
        } catch (Exception ignored) {
            return false;
        }
    }

    private long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private void safeHook(Runnable invocation) {
        try {
            invocation.run();
        } catch (Throwable ignored) {
            // Evaluation instrumentation must never affect routing.
        }
    }
}
