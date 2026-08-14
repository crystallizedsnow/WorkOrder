package com.aiassistant.channel;

import com.aiassistant.channel.model.InboundMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RedisChannelSessionStore implements ChannelSessionStore {
    private final StringRedisTemplate redis;
    @Value("${workorder.channel.session-ttl:P30D}") private Duration ttl;

    @Override
    public Long resolve(InboundMessage message, String userId) {
        String key = "channel:session:" + message.channel() + ":" + safe(message.tenantId()) + ":"
                + safe(message.botAccountId()) + ":" + safe(message.conversationId()) + ":" + userId;
        String value = redis.opsForValue().get(key);
        if (value != null) { redis.expire(key, ttl); return Long.valueOf(value); }
        long generated = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        Boolean created = redis.opsForValue().setIfAbsent(key, Long.toString(generated), ttl);
        if (Boolean.TRUE.equals(created)) return generated;
        return Long.valueOf(redis.opsForValue().get(key));
    }

    private String safe(String value) { return value == null ? "_" : value; }
}
