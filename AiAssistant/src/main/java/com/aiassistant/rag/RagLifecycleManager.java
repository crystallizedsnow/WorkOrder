package com.aiassistant.rag;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "llm.embedding", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RagLifecycleManager implements ApplicationRunner {
    private final KnowledgeBaseIngestor ingestor;
    private final VectorStore vectorStore;
    private final RagStateService state;
    private final TaskExecutor taskExecutor;
    private final MeterRegistry meters;

    public RagLifecycleManager(KnowledgeBaseIngestor ingestor, VectorStore vectorStore, RagStateService state,
                               @Qualifier("ragTaskExecutor") TaskExecutor ragTaskExecutor,
                               ObjectProvider<MeterRegistry> meters) {
        this.ingestor = ingestor;
        this.vectorStore = vectorStore;
        this.state = state;
        this.taskExecutor = ragTaskExecutor;
        this.meters = meters.getIfAvailable();
    }

    @Override
    public void run(ApplicationArguments args) {
        rebuildAsync();
    }

    public boolean rebuildAsync() {
        if (state.snapshot().status() == RagStatus.BUILDING) return false;
        state.building();
        try {
            taskExecutor.execute(this::rebuild);
            return true;
        } catch (RuntimeException rejected) {
            state.failed(rejected, vectorStore.hasPublishedIndex());
            log.warn("RAG 重建任务提交失败: {}", rejected.getMessage());
            return false;
        }
    }

    public synchronized void rebuild() {
        if (state.snapshot().status() != RagStatus.BUILDING) state.building();
        Timer.Sample sample = meters == null ? null : Timer.start(meters);
        try {
            RagBuildResult result = ingestor.ingest();
            state.ready(result);
            if (meters != null) {
                meters.counter("rag.build.total", "result", "success").increment();
            }
        } catch (Exception e) {
            state.failed(e, vectorStore.hasPublishedIndex());
            if (meters != null) meters.counter("rag.build.total", "result", "failure").increment();
            log.error("受管 RAG 构建失败，status={}", state.snapshot().status(), e);
        } finally {
            if (sample != null) sample.stop(meters.timer("rag.build.duration"));
        }
    }

    public boolean rollback() {
        boolean success = vectorStore.rollback();
        if (meters != null) meters.counter("rag.rollback.total", "result", success ? "success" : "failure").increment();
        return success;
    }
}
