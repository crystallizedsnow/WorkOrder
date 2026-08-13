package com.aiassistant.channel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ChannelRateLimiter {
    private final int limit;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public ChannelRateLimiter(@Value("${workorder.channel.feishu.max-requests-per-minute:30}") int limit) {
        this(limit, Clock.systemUTC());
    }
    ChannelRateLimiter(int limit, Clock clock) { this.limit = limit; this.clock = clock; }

    public boolean allow(String key) {
        long minute = clock.instant().getEpochSecond() / 60;
        Window window = windows.compute(key, (ignored, old) -> old == null || old.minute != minute ? new Window(minute) : old);
        return window.count.incrementAndGet() <= limit;
    }
    private static final class Window { final long minute; final AtomicInteger count = new AtomicInteger(); Window(long minute){this.minute=minute;} }
}
