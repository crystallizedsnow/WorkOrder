package com.aiassistant.rag;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 可按需运行的离线 golden set 召回评测，不在应用启动路径执行。 */
@Component
public class RagEvaluationService {
    private final ContentRetriever retriever;

    public RagEvaluationService(ContentRetriever retriever) { this.retriever = retriever; }

    public EvaluationResult evaluate() {
        List<EvaluationCase> cases = loadCases();
        int hits = 0;
        List<String> misses = new ArrayList<>();
        for (EvaluationCase item : cases) {
            boolean hit = retriever.retrieve(item.question()).stream()
                    .anyMatch(document -> item.expectedSourceId().equals(document.getSource()));
            if (hit) hits++; else misses.add(item.question());
        }
        double recallAtK = cases.isEmpty() ? 0.0 : (double) hits / cases.size();
        return new EvaluationResult(cases.size(), hits, recallAtK, misses);
    }

    List<EvaluationCase> loadCases() {
        ClassPathResource resource = new ClassPathResource("knowledge-base/rag-golden-set.tsv");
        List<EvaluationCase> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\\t", -1);
                if (fields.length != 2 || fields[0].isBlank() || fields[1].isBlank()) {
                    throw new IllegalStateException("非法 golden set 行: " + line);
                }
                result.add(new EvaluationCase(fields[0].trim(), fields[1].trim()));
            }
        } catch (Exception e) {
            throw new IllegalStateException("RAG golden set 加载失败", e);
        }
        return result;
    }

    record EvaluationCase(String question, String expectedSourceId) {}
    public record EvaluationResult(int total, int hits, double recallAtK, List<String> misses) {}
}
