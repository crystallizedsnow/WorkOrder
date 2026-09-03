package com.aiassistant.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record DelayedNotificationItem(
        @NotBlank @Size(max = 36) String eventId,
        @NotNull Long userId,
        @NotBlank @Size(max = 128) String tenantKey,
        @NotBlank @Size(max = 128) String openId,
        @NotNull Long workOrderId,
        @NotBlank @Size(max = 64) String workOrderCode,
        @NotBlank @Size(max = 200) String title,
        @NotNull OffsetDateTime deadlineTime) {}
