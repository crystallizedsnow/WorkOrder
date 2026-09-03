package com.example.spring_vue_demo.service;

import com.example.spring_vue_demo.config.AuthProperties;
import com.example.spring_vue_demo.entity.Staff;
import com.example.spring_vue_demo.entity.WorkOrder;
import com.example.spring_vue_demo.enums.WorkOrderStatusEnum;
import com.example.spring_vue_demo.mapper.StaffMapper;
import com.example.spring_vue_demo.mapper.WorkOrderMapper;
import com.example.spring_vue_demo.service.helper.WorkOrderHelper;
import com.example.spring_vue_demo.service.producer.WorkOrderMessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class OverdueWorkOrderProcessor {
    private final WorkOrderMapper workOrderMapper;
    private final StaffMapper staffMapper;
    private final WorkOrderHelper workOrderHelper;
    private final WorkOrderMessageProducer workOrderMessageProducer;
    private final DelayedNotificationDeliveryService delayedNotificationDeliveryService;
    private final AuthProperties authProperties;

    public Long requireSystemSenderId() {
        Long senderId = Objects.requireNonNull(authProperties.getSystemSenderId(),
                "workorder.auth.system-sender-id must be configured");
        Staff systemStaff = staffMapper.selectById(senderId);
        if (systemStaff == null || systemStaff.getStatus() == null || systemStaff.getStatus() == 0) {
            throw new IllegalStateException("The configured system sender must be an existing disabled staff account");
        }
        return senderId;
    }

    @Transactional
    public boolean process(Long orderId, LocalDateTime scanTime, Long systemSenderId) {
        int changed = workOrderMapper.markOverdue(orderId, scanTime);
        if (changed != 1) {
            return false;
        }

        WorkOrder order = workOrderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalStateException("Overdue work order disappeared after status update: " + orderId);
        }
        List<Long> receiverIds = workOrderHelper.getUnfinishedHandlerUserIds(orderId);
        if (receiverIds.isEmpty()) {
            log.warn("Overdue work order has no unfinished handler orderId={}, code={}", orderId, order.getCode());
            return true;
        }

        delayedNotificationDeliveryService.enqueue(order, receiverIds);
        Runnable publishMessage = () -> {
            try {
                workOrderMessageProducer.sendWorkOrderMessages(
                        WorkOrderStatusEnum.DELAYED.getValue(), order.getCode(), receiverIds, systemSenderId, true);
            } catch (RuntimeException error) {
                log.error("Failed to publish overdue message after commit orderId={}, code={}",
                        orderId, order.getCode(), error);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishMessage.run();
                }
            });
        } else {
            publishMessage.run();
        }
        return true;
    }
}
