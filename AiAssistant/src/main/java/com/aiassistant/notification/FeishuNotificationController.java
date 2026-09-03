package com.aiassistant.notification;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/internal/channels/feishu/notifications")
@ConditionalOnProperty(name = "workorder.channel.feishu.enabled", havingValue = "true")
public class FeishuNotificationController {
    private final FeishuNotificationService service;
    private final String serviceKey;

    public FeishuNotificationController(FeishuNotificationService service,
            @Value("${workorder.channel.service-key}") String serviceKey) {
        this.service = service;
        this.serviceKey = serviceKey;
    }

    @PostMapping("/work-order-delayed:batch-send")
    public DelayedNotificationResponse send(
            @RequestHeader("X-Workorder-Service-Key") String key,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DelayedNotificationRequest request) {
        if (!MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), serviceKey.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid service identity");
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid idempotency key");
        return service.send(request);
    }
}
