package com.aiassistant.channel;

import com.aiassistant.channel.model.InboundMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RedisChannelSessionStore implements ChannelSessionStore {
    private final StringRedisTemplate redis;

    @Override
    public Long resolve(InboundMessage message, String userId) {
        String key = "channel:session:" + message.channel() + ":" + safe(message.tenantId()) + ":"
                + safe(message.botAccountId()) + ":" + userId;
        String value = redis.opsForValue().get(key);
        if (value != null) return Long.valueOf(value);
        long generated = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).getMostSignificantBits() & Long.MAX_VALUE;
        Boolean created = redis.opsForValue().setIfAbsent(key, Long.toString(generated));
        if (Boolean.TRUE.equals(created)) return generated;
        return Long.valueOf(redis.opsForValue().get(key));
    }

    private String safe(String value) { return value == null ? "_" : value; }
}
