package com.aiassistant.notification;

import com.aiassistant.channel.FeishuChannelAdapter;
import com.aiassistant.channel.FeishuDeliveryException;
import com.aiassistant.channel.FeishuProperties;
import com.aiassistant.channel.ChannelRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FeishuNotificationServiceTest {
    private FeishuNotificationService service;

    @AfterEach void close() { if (service != null) service.shutdown(); }

    @Test void returnsPerRecipientResultsAndDoesNotResendSucceededEvent() {
        FeishuChannelAdapter adapter = mock(FeishuChannelAdapter.class);
        when(adapter.sendCardToOpenId(eq("ou_ok"), anyMap())).thenReturn("om_1");
        service = new FeishuNotificationService(adapter, properties(), limiter());
        DelayedNotificationRequest request = new DelayedNotificationRequest(List.of(item("event-1", "ou_ok")));

        assertThat(service.send(request).results().get(0).status()).isEqualTo("SUCCEEDED");
        assertThat(service.send(request).results().get(0).messageId()).isEqualTo("om_1");
        verify(adapter, times(1)).sendCardToOpenId(eq("ou_ok"), anyMap());
    }

    @Test void classifiesRetryableAndPermanentFailures() {
        FeishuChannelAdapter adapter = mock(FeishuChannelAdapter.class);
        when(adapter.sendCardToOpenId(eq("ou_retry"), anyMap()))
                .thenThrow(new FeishuDeliveryException(true, "RATE_LIMIT", "limited", null));
        when(adapter.sendCardToOpenId(eq("ou_bad"), anyMap()))
                .thenThrow(new FeishuDeliveryException(false, "INVALID_OPEN_ID", "bad", null));
        service = new FeishuNotificationService(adapter, properties(), limiter());

        var results = service.send(new DelayedNotificationRequest(List.of(
                item("event-r", "ou_retry"), item("event-p", "ou_bad")))).results();
        assertThat(results).extracting(DelayedNotificationResult::status)
                .containsExactly("RETRYABLE_FAILED", "PERMANENT_FAILED");
    }

    @Test void delayedCardContainsNoActionButtonAndTruncatesFields() {
        var card = FeishuChannelAdapter.buildDelayedWorkOrderCard("WO1", "x".repeat(300), OffsetDateTime.now());
        String text = card.toString();
        assertThat(text).contains("工单已延期", "WO1", "已超时", "请尽快处理").doesNotContain("button");
    }

    private DelayedNotificationItem item(String event, String openId) {
        return new DelayedNotificationItem(event, 1L, "tenant", openId, 2L,
                "WO1", "测试延期工单", OffsetDateTime.now());
    }

    private FeishuProperties properties() {
        FeishuProperties p = new FeishuProperties();
        p.setNotificationWorkerThreads(2); p.setNotificationQueueCapacity(10);
        return p;
    }

    private ChannelRateLimiter limiter() {
        ChannelRateLimiter limiter = mock(ChannelRateLimiter.class);
        when(limiter.allow(anyString())).thenReturn(true);
        return limiter;
    }
}
