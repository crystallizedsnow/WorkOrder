package com.aiassistant.intent;

public interface IntentEvaluationHook {
    default void onRequestStarted(String query) {}
    default void onRetrievalCompleted(SemanticSkillRetriever.RetrievalResult retrieval, long durationMs) {}
    default void onModelCompleted(IntentModelClient.ModelProposal proposal, long durationMs) {}
    default void onPolicyCompleted(RoutingDecision decision, long durationMs) {}
    default void onFailed(String stage, Throwable error, long durationMs) {}
}
