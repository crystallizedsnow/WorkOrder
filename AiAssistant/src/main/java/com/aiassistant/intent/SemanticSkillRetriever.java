package com.aiassistant.intent;

import com.aiassistant.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class SemanticSkillRetriever {
    private final SkillRegistry skillRegistry;
    private final DomainScopeRegistry domainScopeRegistry;
    private final ObjectProvider<EmbeddingModel> embeddingModels;
    private final IntentRoutingProperties properties;
    private volatile IndexSnapshot index = new IndexSnapshot("", List.of(), List.of(), List.of(), false);

    public SemanticSkillRetriever(SkillRegistry skillRegistry, DomainScopeRegistry domainScopeRegistry,
                                  ObjectProvider<EmbeddingModel> embeddingModels,
                                  IntentRoutingProperties properties) {
        this.skillRegistry = skillRegistry;
        this.domainScopeRegistry = domainScopeRegistry;
        this.embeddingModels = embeddingModels;
        this.properties = properties;
    }

    public RetrievalResult retrieve(String query) {
        IndexSnapshot current = ensureIndex();
        if (!current.embeddingAvailable()) {
            List<RoutingDecision.SemanticCandidate> fallback = skillRegistry.snapshot().skills().keySet().stream()
                    .limit(Math.max(1, properties.getTopK()))
                    .map(key -> new RoutingDecision.SemanticCandidate(key, 0, 0, 0))
                    .toList();
            return new RetrievalResult(fallback, 0, 0, false);
        }
        EmbeddingModel model = embeddingModels.getIfAvailable();
        float[] queryVector = model == null ? new float[0] : model.embed(query);
        if (queryVector.length == 0) {
            return new RetrievalResult(List.of(), 0, 0, false);
        }
        List<RoutingDecision.SemanticCandidate> candidates = new ArrayList<>();
        for (SkillVectors skill : current.skills()) {
            double positive = maxSimilarity(queryVector, skill.positiveVectors());
            double negative = maxSimilarity(queryVector, skill.negativeVectors());
            candidates.add(new RoutingDecision.SemanticCandidate(skill.skillKey(), positive, negative,
                    positive - negative));
        }
        candidates.sort(Comparator.comparingDouble(RoutingDecision.SemanticCandidate::positiveScore).reversed());
        int limit = Math.min(Math.max(1, properties.getTopK()), candidates.size());
        return new RetrievalResult(List.copyOf(candidates.subList(0, limit)),
                maxSimilarity(queryVector, current.domainPositiveVectors()),
                maxSimilarity(queryVector, current.domainNegativeVectors()), true);
    }

    private IndexSnapshot ensureIndex() {
        String version = skillRegistry.snapshot().version();
        IndexSnapshot current = index;
        if (version.equals(current.registryVersion())) return current;
        synchronized (this) {
            current = index;
            if (version.equals(current.registryVersion())) return current;
            EmbeddingModel model = embeddingModels.getIfAvailable();
            if (model == null || !model.isAvailable()) {
                index = new IndexSnapshot(version, List.of(), List.of(), List.of(), false);
                return index;
            }
            List<SkillVectors> skills = new ArrayList<>();
            for (Map.Entry<String, SkillRegistry.SkillCard> entry : skillRegistry.snapshot().skills().entrySet()) {
                SkillRegistry.SkillCard card = entry.getValue();
                List<String> positives = new ArrayList<>();
                positives.add(card.name() + "。" + card.description() + "。能力：" + String.join("、", card.capabilities()));
                positives.addAll(card.positiveExamples());
                skills.add(new SkillVectors(entry.getKey(), model.embedAll(positives),
                        model.embedAll(card.negativeExamples())));
            }
            DomainScopeRegistry.DomainScope domain = domainScopeRegistry.snapshot();
            List<String> domainPositives = new ArrayList<>();
            domainPositives.add(domain.getName() + "。" + domain.getDescription() + "。主题："
                    + String.join("、", domain.getSupportedTopics()));
            domainPositives.addAll(domain.getPositiveExamples());
            index = new IndexSnapshot(version, List.copyOf(skills), model.embedAll(domainPositives),
                    model.embedAll(domain.getNegativeExamples()), true);
            return index;
        }
    }

    private double maxSimilarity(float[] query, List<float[]> vectors) {
        double max = 0;
        for (float[] vector : vectors) {
            if (vector == null || vector.length != query.length || vector.length == 0) continue;
            double dot = 0;
            double qNorm = 0;
            double vNorm = 0;
            for (int i = 0; i < query.length; i++) {
                dot += query[i] * vector[i];
                qNorm += query[i] * query[i];
                vNorm += vector[i] * vector[i];
            }
            if (qNorm > 0 && vNorm > 0) max = Math.max(max, dot / Math.sqrt(qNorm * vNorm));
        }
        return max;
    }

    public record RetrievalResult(List<RoutingDecision.SemanticCandidate> candidates,
                                  double domainPositiveScore, double domainNegativeScore,
                                  boolean embeddingAvailable) {}

    private record SkillVectors(String skillKey, List<float[]> positiveVectors, List<float[]> negativeVectors) {}

    private record IndexSnapshot(String registryVersion, List<SkillVectors> skills,
                                 List<float[]> domainPositiveVectors, List<float[]> domainNegativeVectors,
                                 boolean embeddingAvailable) {}
}
