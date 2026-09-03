package com.example.spring_vue_demo.service;

import com.example.spring_vue_demo.config.AuthProperties;
import com.example.spring_vue_demo.entity.WorkOrder;
import com.example.spring_vue_demo.mapper.StaffMapper;
import com.example.spring_vue_demo.mapper.WorkOrderMapper;
import com.example.spring_vue_demo.service.helper.WorkOrderHelper;
import com.example.spring_vue_demo.service.producer.WorkOrderMessageProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OverdueWorkOrderProcessorTest {
    @Mock WorkOrderMapper workOrderMapper;
    @Mock StaffMapper staffMapper;
    @Mock WorkOrderHelper workOrderHelper;
    @Mock WorkOrderMessageProducer workOrderMessageProducer;
    @Mock DelayedNotificationDeliveryService delayedNotificationDeliveryService;
    @Mock AuthProperties authProperties;
    @InjectMocks OverdueWorkOrderProcessor processor;

    @Test
    void createsNotificationsOnlyAfterConditionalStatusUpdateSucceeds() {
        WorkOrder order = new WorkOrder();
        order.setId(9L);
        order.setCode("WO9");
        when(workOrderMapper.markOverdue(any(), any())).thenReturn(1);
        when(workOrderMapper.selectById(9L)).thenReturn(order);
        when(workOrderHelper.getUnfinishedHandlerUserIds(9L)).thenReturn(List.of(2L, 3L));

        boolean processed = processor.process(9L, LocalDateTime.now(), 99L);

        assertThat(processed).isTrue();
        verify(delayedNotificationDeliveryService).enqueue(order, List.of(2L, 3L));
        verify(workOrderMessageProducer).sendWorkOrderMessages(410, "WO9", List.of(2L, 3L), 99L, true);
    }

    @Test
    void skipsNotificationsWhenAnotherInstanceAlreadyChangedTheOrder() {
        when(workOrderMapper.markOverdue(any(), any())).thenReturn(0);

        boolean processed = processor.process(9L, LocalDateTime.now(), 99L);

        assertThat(processed).isFalse();
        verify(delayedNotificationDeliveryService, never()).enqueue(any(), any());
        verify(workOrderMessageProducer, never()).sendWorkOrderMessages(any(), any(), any(), any(), anyBoolean());
    }
}
