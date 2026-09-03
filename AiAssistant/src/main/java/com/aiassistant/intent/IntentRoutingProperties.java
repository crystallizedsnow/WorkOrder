package com.aiassistant.intent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "workorder.intent-routing")
@Data
public class IntentRoutingProperties {
    private boolean enabled = true;
    private Mode mode = Mode.SHADOW;
    private String domainScope = "classpath:intent/domain-scope.json";
    private int topK = 3;
    private int workerThreads = 2;
    private int queueCapacity = 100;
    private int timeoutMs = 15000;
    private int maxTokens = 512;
    private double temperature = 0.0;
    private String thinkingType = "disabled";
    private String model;
    private String policyVersion = "intent-policy-v1-shadow";
    private Evaluation evaluation = new Evaluation();
    private EvaluationHook evaluationHook = new EvaluationHook();

    @Data
    public static class Evaluation {
        private boolean enabled = false;
        private String dataset = "classpath:intent/golden-set.json";
        private String outputDir = "./test-output/intent-evaluation";
        private String datasetVersion = "intent-eval-50-v1";
    }

    @Data
    public static class EvaluationHook {
        private boolean enabled = false;
        private String outputFile = "./test-output/intent-evaluation/hook-events.jsonl";
    }

    public enum Mode { OFF, SHADOW, ENFORCE }
}
