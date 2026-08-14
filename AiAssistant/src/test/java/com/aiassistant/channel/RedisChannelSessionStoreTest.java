package com.aiassistant.channel;

import com.aiassistant.channel.model.ChannelType;
import com.aiassistant.channel.model.InboundMessage;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisChannelSessionStoreTest {
    @Test void keyContainsSourceConversationAndRefreshesTtl() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("channel:session:FEISHU:tenant:bot:chat-1:user-1")).thenReturn("88");
        RedisChannelSessionStore store = new RedisChannelSessionStore(redis);
        ReflectionTestUtils.setField(store, "ttl", Duration.ofDays(30));
        Long result = store.resolve(new InboundMessage(ChannelType.FEISHU, "bot", "tenant", "chat-1", "m", "sender",
                "union", "hello", "text", "trace", Instant.now(), Map.of()), "user-1");
        assertEquals(88L, result);
        verify(redis).expire("channel:session:FEISHU:tenant:bot:chat-1:user-1", Duration.ofDays(30));
    }
}
