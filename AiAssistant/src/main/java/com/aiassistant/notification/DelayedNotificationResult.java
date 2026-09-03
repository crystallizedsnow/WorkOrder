package com.aiassistant.notification;

public record DelayedNotificationResult(String eventId, String status, String messageId,
                                        String errorCode, String errorMessage) {}
