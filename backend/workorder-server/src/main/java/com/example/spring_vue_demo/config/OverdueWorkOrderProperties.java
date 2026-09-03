package com.example.spring_vue_demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("workorder.overdue")
public class OverdueWorkOrderProperties {
    private boolean enabled = true;
    private long scanDelayMs = 30_000L;
    private int batchSize = 200;
    private int maxBatchesPerRun = 10;
}
