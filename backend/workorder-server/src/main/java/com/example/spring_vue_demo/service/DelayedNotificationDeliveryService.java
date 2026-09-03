package com.example.spring_vue_demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.spring_vue_demo.config.AuthProperties;
import com.example.spring_vue_demo.config.DelayedNotificationProperties;
import com.example.spring_vue_demo.entity.NotificationDelivery;
import com.example.spring_vue_demo.entity.WorkOrder;
import com.example.spring_vue_demo.mapper.ExternalIdentityBindingMapper;
import com.example.spring_vue_demo.mapper.NotificationDeliveryMapper;
import com.example.spring_vue_demo.mapper.WorkOrderMapper;
import com.example.workorder.api.dto.ResolvedChannelBinding;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DelayedNotificationDeliveryService {
    private static final String EVENT_TYPE = "WORK_ORDER_DELAYED";
    private static final String CHANNEL = "FEISHU";
    private final NotificationDeliveryMapper deliveryMapper;
    private final ExternalIdentityBindingMapper bindingMapper;
    private final WorkOrderMapper workOrderMapper;
    private final DelayedNotificationProperties properties;
    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;

    public void enqueue(WorkOrder order, List<Long> receiverIds) {
        for (Long receiverId : receiverIds.stream().filter(Objects::nonNull).distinct().toList()) {
            NotificationDelivery task = new NotificationDelivery();
            task.setEventId(UUID.randomUUID().toString());
            task.setEventType(EVENT_TYPE); task.setWorkOrderId(order.getId()); task.setWorkOrderCode(order.getCode());
            task.setReceiverId(receiverId); task.setChannel(CHANNEL); task.setStatus("PENDING"); task.setAttempts(0);
            task.setCreatedAt(LocalDateTime.now());
            try { deliveryMapper.insert(task); }
            catch (DuplicateKeyException ignored) { log.info("Delayed notification already exists orderId={}, receiverId={}", order.getId(), receiverId); }
        }
    }

    @Scheduled(fixedDelayString = "${workorder.notification.dispatch-delay-ms:5000}")
    public void dispatchPending() {
        LocalDateTime now = LocalDateTime.now();
        recoverExpiredSending(now);
        List<NotificationDelivery> candidates = deliveryMapper.selectList(new LambdaQueryWrapper<NotificationDelivery>()
                .eq(NotificationDelivery::getChannel, CHANNEL)
                .in(NotificationDelivery::getStatus, List.of("PENDING", "RETRYING"))
                .and(w -> w.isNull(NotificationDelivery::getNextRetryTime).or().le(NotificationDelivery::getNextRetryTime, now))
                .lt(NotificationDelivery::getAttempts, properties.getMaxAttempts())
                .orderByAsc(NotificationDelivery::getId).last("LIMIT " + properties.getBatchSize()));
        if (candidates.isEmpty()) return;
        List<NotificationDelivery> claimed = candidates.stream().filter(this::claim).toList();
        if (claimed.isEmpty()) return;

        List<Long> userIds = claimed.stream().map(NotificationDelivery::getReceiverId).distinct().toList();
        Map<Long, ResolvedChannelBinding> bindings = bindingMapper.resolveActiveBindings("FEISHU", userIds).stream()
                .collect(Collectors.toMap(ResolvedChannelBinding::getUserId, Function.identity(), (a, b) -> a));
        List<NotificationDelivery> sendable = new ArrayList<>();
        for (NotificationDelivery task : claimed) {
            if (!bindings.containsKey(task.getReceiverId())) mark(task, "SKIPPED_UNBOUND", null, null);
            else sendable.add(task);
        }
        if (sendable.isEmpty()) return;
        sendBatch(sendable, bindings);
    }

    private boolean claim(NotificationDelivery task) {
        int changed = deliveryMapper.update(null, new LambdaUpdateWrapper<NotificationDelivery>()
                .eq(NotificationDelivery::getId, task.getId())
                .in(NotificationDelivery::getStatus, List.of("PENDING", "RETRYING"))
                .set(NotificationDelivery::getStatus, "SENDING")
                .set(NotificationDelivery::getAttempts, task.getAttempts() + 1)
                .set(NotificationDelivery::getSendingStartedAt, LocalDateTime.now()));
        if (changed == 1) task.setAttempts(task.getAttempts() + 1);
        return changed == 1;
    }

    private void sendBatch(List<NotificationDelivery> tasks, Map<Long, ResolvedChannelBinding> bindings) {
        List<Map<String, Object>> notifications = new ArrayList<>();
        for (NotificationDelivery task : tasks) {
            WorkOrder order = workOrderMapper.selectById(task.getWorkOrderId());
            if (order == null) { mark(task, "FAILED", "WORK_ORDER_MISSING", null); continue; }
            ResolvedChannelBinding binding = bindings.get(task.getReceiverId());
            notifications.add(Map.of("eventId", task.getEventId(), "userId", task.getReceiverId(),
                    "tenantKey", binding.getTenantKey(), "openId", binding.getOpenId(),
                    "workOrderId", task.getWorkOrderId(), "workOrderCode", task.getWorkOrderCode(),
                    "title", order.getTitle(), "deadlineTime", order.getDeadlineTime().atZone(ZoneId.systemDefault()).toOffsetDateTime()));
        }
        if (notifications.isEmpty()) return;
        try {
            String raw = RestClient.create().post()
                    .uri(properties.getAiAssistantUrl() + "/internal/channels/feishu/notifications/work-order-delayed:batch-send")
                    .header("X-Workorder-Service-Key", authProperties.getChannelServiceKey())
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of("notifications", notifications))
                    .retrieve().body(String.class);
            JsonNode results = objectMapper.readTree(raw).path("results");
            Map<String, NotificationDelivery> byEvent = tasks.stream().collect(Collectors.toMap(NotificationDelivery::getEventId, Function.identity()));
            Set<String> processed = new HashSet<>();
            for (JsonNode result : results) {
                NotificationDelivery task = byEvent.get(result.path("eventId").asText());
                if (task == null) continue;
                processed.add(task.getEventId());
                String status = result.path("status").asText();
                if ("SUCCEEDED".equals(status)) mark(task, "SUCCEEDED", null, LocalDateTime.now());
                else if ("RETRYABLE_FAILED".equals(status)) retry(task, result.path("errorCode").asText());
                else mark(task, "FAILED", result.path("errorCode").asText(), null);
            }
            tasks.stream().filter(task -> !processed.contains(task.getEventId()))
                    .forEach(task -> retry(task, "MISSING_BATCH_RESULT"));
        } catch (Exception ex) {
            tasks.forEach(task -> retry(task, safeError(ex)));
        }
    }

    private void retry(NotificationDelivery task, String error) {
        if (task.getAttempts() >= properties.getMaxAttempts()) { mark(task, "FAILED", error, null); return; }
        long multiplier = 1L << Math.max(0, task.getAttempts() - 1);
        LocalDateTime next = LocalDateTime.now().plus(properties.getInitialRetryDelay().multipliedBy(multiplier));
        deliveryMapper.update(null, new LambdaUpdateWrapper<NotificationDelivery>().eq(NotificationDelivery::getId, task.getId())
                .set(NotificationDelivery::getStatus, "RETRYING").set(NotificationDelivery::getNextRetryTime, next)
                .set(NotificationDelivery::getSendingStartedAt, null)
                .set(NotificationDelivery::getLastError, error));
    }

    private void mark(NotificationDelivery task, String status, String error, LocalDateTime sentAt) {
        deliveryMapper.update(null, new LambdaUpdateWrapper<NotificationDelivery>().eq(NotificationDelivery::getId, task.getId())
                .set(NotificationDelivery::getStatus, status).set(NotificationDelivery::getLastError, error)
                .set(NotificationDelivery::getSentAt, sentAt).set(NotificationDelivery::getNextRetryTime, null)
                .set(NotificationDelivery::getSendingStartedAt, null));
    }

    private void recoverExpiredSending(LocalDateTime now) {
        int recovered = deliveryMapper.update(null, new LambdaUpdateWrapper<NotificationDelivery>()
                .eq(NotificationDelivery::getChannel, CHANNEL)
                .eq(NotificationDelivery::getStatus, "SENDING")
                .lt(NotificationDelivery::getSendingStartedAt, now.minus(properties.getSendingLease()))
                .set(NotificationDelivery::getStatus, "RETRYING")
                .set(NotificationDelivery::getNextRetryTime, now)
                .set(NotificationDelivery::getSendingStartedAt, null)
                .set(NotificationDelivery::getLastError, "SENDING_LEASE_EXPIRED"));
        if (recovered > 0) {
            log.warn("Recovered expired delayed-notification tasks count={}", recovered);
        }
    }

    private String safeError(Exception ex) {
        String value = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
