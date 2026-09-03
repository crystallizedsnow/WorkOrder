package com.aiassistant.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DelayedNotificationRequest(
        @NotEmpty @Size(max = 100) List<@Valid DelayedNotificationItem> notifications) {}
