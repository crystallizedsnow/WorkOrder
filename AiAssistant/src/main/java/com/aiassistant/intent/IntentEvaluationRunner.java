package com.aiassistant.intent;

import com.aiassistant.channel.model.AgentRequest;
import com.aiassistant.channel.model.ChannelType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "workorder.intent-routing.evaluation", name = "enabled", havingValue = "true")
@Slf4j
public class IntentEvaluationRunner implements ApplicationRunner {
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final IntentShadowRouter router;
    private final IntentRoutingProperties properties;
    private final ResourceLoader resources;

    public IntentEvaluationRunner(IntentShadowRouter router, IntentRoutingProperties properties,
                                  ResourceLoader resources) {
        this.router = router;
        this.properties = properties;
        this.resources = resources;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String runId = "intent-eval-" + Instant.now().toString().replaceAll("[:.]", "-");
        JsonNode dataset = MAPPER.readTree(resources.getResource(properties.getEvaluation().getDataset()).getInputStream());
        if (!dataset.isArray() || dataset.size() != 50) {
            throw new IllegalStateException("离线意图评测集必须恰好包含 50 条，实际为 " + dataset.size());
        }
        List<Map<String, Object>> results = new ArrayList<>();
        Map<RouteType, Counts> routeCounts = new EnumMap<>(RouteType.class);
        Map<String, Counts> categoryCounts = new LinkedHashMap<>();
        int correct = 0;
        int skillCases = 0;
        int skillCorrect = 0;
        int failures = 0;
        List<Long> durations = new ArrayList<>();
        int index = 0;
        for (JsonNode item : dataset) {
            index++;
            String caseId = item.path("caseId").asText("INTENT-%03d".formatted(index));
            String query = item.path("query").asText();
            String category = item.path("category").asText("unknown");
            RouteType expected = RouteType.valueOf(item.path("expectedRoute").asText());
            Set<String> expectedSkills = stringSet(item.path("expectedSkills"));
            long started = System.nanoTime();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("caseId", caseId);
            row.put("category", category);
            row.put("query", query);
            row.put("expectedRoute", expected);
            row.put("expectedSkills", expectedSkills);
            try {
                AgentRequest request = new AgentRequest((long) index, "offline-evaluator", query,
                        "offline-no-business-access", ChannelType.WEB, "offline", "offline", "offline", caseId);
                RoutingDecision actual = router.route(request);
                boolean routeCorrect = expected == actual.routeType();
                boolean skillsCorrect = expectedSkills.equals(actual.allowedSkillKeys());
                boolean passed = routeCorrect && (expected != RouteType.SKILL_EXECUTION || skillsCorrect);
                if (passed) correct++;
                if (expected == RouteType.SKILL_EXECUTION) {
                    skillCases++;
                    if (skillsCorrect) skillCorrect++;
                }
                routeCounts.computeIfAbsent(expected, ignored -> new Counts()).expected++;
                if (routeCorrect) routeCounts.get(expected).correct++;
                categoryCounts.computeIfAbsent(category, ignored -> new Counts()).expected++;
                if (passed) categoryCounts.get(category).correct++;
                durations.add(actual.durationMs());
                row.put("actualRoute", actual.routeType());
                row.put("actualSkills", actual.allowedSkillKeys());
                row.put("actualIntents", actual.intents());
                row.put("reasonCode", actual.reasonCode());
                row.put("embeddingAvailable", actual.embeddingAvailable());
                row.put("durationMs", actual.durationMs());
                row.put("passed", passed);
            } catch (Throwable error) {
                failures++;
                routeCounts.computeIfAbsent(expected, ignored -> new Counts()).expected++;
                categoryCounts.computeIfAbsent(category, ignored -> new Counts()).expected++;
                row.put("passed", false);
                row.put("error", error.getClass().getSimpleName() + ": " + error.getMessage());
            }
            results.add(row);
            log.info("INTENT-EVAL caseId={} category={} passed={} actualRoute={} durationMs={}", caseId, category,
                    row.get("passed"), row.get("actualRoute"), row.get("durationMs"));
        }
        durations.sort(Long::compareTo);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("runId", runId);
        summary.put("datasetVersion", properties.getEvaluation().getDatasetVersion());
        summary.put("policyVersion", properties.getPolicyVersion());
        summary.put("model", properties.getModel());
        summary.put("total", results.size());
        summary.put("passed", correct);
        summary.put("accuracy", ratio(correct, results.size()));
        summary.put("skillExactMatch", ratio(skillCorrect, skillCases));
        summary.put("failures", failures);
        summary.put("p50DurationMs", percentile(durations, 0.50));
        summary.put("p95DurationMs", percentile(durations, 0.95));
        summary.put("p99DurationMs", percentile(durations, 0.99));
        summary.put("routes", renderCounts(routeCounts));
        summary.put("categories", renderCounts(categoryCounts));
        summary.put("results", results);

        Path outputDir = Path.of(properties.getEvaluation().getOutputDir()).toAbsolutePath().normalize();
        Files.createDirectories(outputDir);
        Path json = outputDir.resolve("intent-evaluation-result.json");
        Path markdown = outputDir.resolve("intent-evaluation-report.md");
        Files.writeString(json, MAPPER.writeValueAsString(summary), StandardCharsets.UTF_8);
        Files.writeString(markdown, markdown(summary, results), StandardCharsets.UTF_8);
        log.info("INTENT-EVAL-COMPLETED runId={} passed={}/{} accuracy={} report={}", runId, correct,
                results.size(), summary.get("accuracy"), markdown);
    }

    private Set<String> stringSet(JsonNode node) {
        Set<String> values = new LinkedHashSet<>();
        node.forEach(value -> values.add(value.asText()));
        return values;
    }

    private double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0 : Math.round(numerator * 10000.0 / denominator) / 10000.0;
    }

    private long percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) return 0;
        int index = Math.max(0, (int) Math.ceil(values.size() * percentile) - 1);
        return values.get(index);
    }

    private Map<String, Map<String, Object>> renderCounts(Map<?, Counts> counts) {
        Map<String, Map<String, Object>> rendered = new LinkedHashMap<>();
        counts.forEach((key, value) -> rendered.put(key.toString(), Map.of(
                "total", value.expected, "passed", value.correct, "recall", ratio(value.correct, value.expected))));
        return rendered;
    }

    @SuppressWarnings("unchecked")
    private String markdown(Map<String, Object> summary, List<Map<String, Object>> results) {
        StringBuilder text = new StringBuilder("# 意图识别离线评测报告\n\n");
        text.append("- Run ID：`").append(summary.get("runId")).append("`\n")
                .append("- 数据集：`").append(summary.get("datasetVersion")).append("`（50 条）\n")
                .append("- 模型：`").append(summary.get("model")).append("`\n")
                .append("- 通过：").append(summary.get("passed")).append("/50\n")
                .append("- 准确率：").append(summary.get("accuracy")).append("\n")
                .append("- Skill Exact Match：").append(summary.get("skillExactMatch")).append("\n")
                .append("- 失败调用：").append(summary.get("failures")).append("\n")
                .append("- 时延：P50 ").append(summary.get("p50DurationMs")).append(" ms，P95 ")
                .append(summary.get("p95DurationMs")).append(" ms，P99 ").append(summary.get("p99DurationMs")).append(" ms\n\n")
                .append("## 分类结果\n\n| 类别 | 通过/总数 | 准确率 |\n|---|---:|---:|\n");
        Map<String, Map<String, Object>> categories = (Map<String, Map<String, Object>>) summary.get("categories");
        categories.forEach((name, value) -> text.append("|").append(name).append("|")
                .append(value.get("passed")).append("/").append(value.get("total")).append("|")
                .append(value.get("recall")).append("|\n"));
        text.append("\n## 未通过案例\n\n| Case | 类别 | 期望 | 实际 | 原因/错误 |\n|---|---|---|---|---|\n");
        results.stream().filter(row -> !Boolean.TRUE.equals(row.get("passed"))).forEach(row -> text.append("|")
                .append(row.get("caseId")).append("|").append(row.get("category")).append("|")
                .append(row.get("expectedRoute")).append("|").append(row.getOrDefault("actualRoute", "ERROR"))
                .append("|").append(String.valueOf(row.getOrDefault("reasonCode", row.getOrDefault("error", "")))
                        .replace("|", "\\|").replace("\n", " ")).append("|\n"));
        return text.toString();
    }

    private static class Counts {
        int expected;
        int correct;
    }
}
