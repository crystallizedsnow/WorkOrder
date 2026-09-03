package com.example.spring_vue_demo.service;

import com.example.spring_vue_demo.config.OverdueWorkOrderProperties;
import com.example.spring_vue_demo.entity.WorkOrder;
import com.example.spring_vue_demo.mapper.WorkOrderMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OverdueWorkOrderScannerTest {
    @Test
    void scansCandidatesInABoundedBatchAndDelegatesProcessing() {
        WorkOrderMapper mapper = mock(WorkOrderMapper.class);
        OverdueWorkOrderProcessor processor = mock(OverdueWorkOrderProcessor.class);
        OverdueWorkOrderProperties properties = new OverdueWorkOrderProperties();
        properties.setBatchSize(200);
        properties.setMaxBatchesPerRun(10);
        WorkOrder candidate = new WorkOrder();
        candidate.setId(7L);
        when(mapper.selectOverdueCandidates(any(), anyInt())).thenReturn(List.of(candidate));
        when(processor.requireSystemSenderId()).thenReturn(99L);
        when(processor.process(any(), any(), any())).thenReturn(true);

        new OverdueWorkOrderScanner(mapper, processor, properties).scan();

        verify(processor).process(any(), any(), any());
    }
}
