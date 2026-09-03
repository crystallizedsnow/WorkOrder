package com.example.spring_vue_demo.service;

import com.example.spring_vue_demo.entity.NotificationDelivery;
import com.example.spring_vue_demo.entity.WorkOrder;
import com.example.spring_vue_demo.mapper.NotificationDeliveryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DelayedNotificationDeliveryServiceTest {
    @Mock NotificationDeliveryMapper deliveryMapper;
    @InjectMocks DelayedNotificationDeliveryService service;

    @Test void createsOneFeishuTaskPerDistinctReceiver() {
        WorkOrder order = new WorkOrder(); order.setId(9L); order.setCode("WO9");
        service.enqueue(order, List.of(2L, 2L, 3L));
        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(NotificationDelivery::getReceiverId).containsExactly(2L, 3L);
        assertThat(captor.getAllValues()).allSatisfy(task -> {
            assertThat(task.getEventType()).isEqualTo("WORK_ORDER_DELAYED");
            assertThat(task.getChannel()).isEqualTo("FEISHU");
            assertThat(task.getStatus()).isEqualTo("PENDING");
        });
    }
}
