package com.aiassistant.channel;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisMessageDeduplicator implements MessageDeduplicator {
    private final StringRedisTemplate redis;
    @Value("${workorder.channel.dedup-ttl:PT24H}") private Duration ttl;

    @Override
    public boolean claim(String channel, String messageId) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("channel:dedup:" + channel + ":" + messageId, "1", ttl));
    }
}
