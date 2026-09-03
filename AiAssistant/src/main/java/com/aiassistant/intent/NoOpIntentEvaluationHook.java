package com.aiassistant.intent;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "workorder.intent-routing.evaluation-hook", name = "enabled",
        havingValue = "false", matchIfMissing = true)
public class NoOpIntentEvaluationHook implements IntentEvaluationHook {
}
