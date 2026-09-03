package com.example.spring_vue_demo.service;

import com.example.spring_vue_demo.config.OverdueWorkOrderProperties;
import com.example.spring_vue_demo.entity.WorkOrder;
import com.example.spring_vue_demo.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "workorder.overdue.enabled", havingValue = "true", matchIfMissing = true)
public class OverdueWorkOrderScanner {
    private final WorkOrderMapper workOrderMapper;
    private final OverdueWorkOrderProcessor processor;
    private final OverdueWorkOrderProperties properties;

    @Scheduled(fixedDelayString = "${workorder.overdue.scan-delay-ms:30000}")
    public void scan() {
        LocalDateTime scanTime = LocalDateTime.now();
        Long systemSenderId = processor.requireSystemSenderId();
        int scanned = 0;
        int updated = 0;
        int failed = 0;

        for (int batch = 0; batch < Math.max(1, properties.getMaxBatchesPerRun()); batch++) {
            List<WorkOrder> candidates = findCandidates(scanTime);
            if (candidates.isEmpty()) {
                break;
            }
            scanned += candidates.size();
            for (WorkOrder candidate : candidates) {
                try {
                    if (processor.process(candidate.getId(), scanTime, systemSenderId)) {
                        updated++;
                    }
                } catch (RuntimeException error) {
                    failed++;
                    log.error("Failed to process overdue work order orderId={}", candidate.getId(), error);
                }
            }
            if (candidates.size() < normalizedBatchSize()) {
                break;
            }
        }
        log.info("Overdue scan completed scanned={}, updated={}, failed={}, scanTime={}",
                scanned, updated, failed, scanTime);
    }

    private List<WorkOrder> findCandidates(LocalDateTime scanTime) {
        return workOrderMapper.selectOverdueCandidates(scanTime, normalizedBatchSize());
    }

    private int normalizedBatchSize() {
        return Math.max(1, properties.getBatchSize());
    }
}
