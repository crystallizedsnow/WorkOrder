package com.example.workorder.cli.preview;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Repository
public class RedisWritePreviewStore implements WritePreviewStore {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final String keyPrefix;

    public RedisWritePreviewStore(StringRedisTemplate redis, ObjectMapper mapper,
                                  @Value("${workorder.write-preview.key-prefix:workorder:write-preview:}") String keyPrefix) {
        this.redis = redis;
        this.mapper = mapper;
        this.keyPrefix = keyPrefix;
    }

    public void save(WritePreview preview, Duration ttl) {
        try {
            redis.opsForValue().set(key(preview.getPreviewId()), mapper.writeValueAsString(preview), ttl);
        } catch (Exception error) {
            throw new IllegalStateException("write preview store unavailable", error);
        }
    }

    public Optional<WritePreview> find(String previewId) {
        try {
            String value = redis.opsForValue().get(key(previewId));
            return value == null ? Optional.empty() : Optional.of(mapper.readValue(value, WritePreview.class));
        } catch (Exception error) {
            throw new IllegalStateException("write preview store unavailable", error);
        }
    }

    public Optional<WritePreview> transition(String previewId, WritePreviewStatus expected, WritePreviewStatus target) {
        // 当前项目以单实例 CLI Service 运行；多实例部署时替换为 Redis Lua CAS。
        synchronized ((keyPrefix + previewId).intern()) {
            Optional<WritePreview> current = find(previewId);
            if (current.isEmpty() || current.get().getStatus() != expected) return Optional.empty();
            WritePreview value = current.get();
            value.setStatus(target);
            Duration remaining = Duration.between(Instant.now(), value.getExpiresAt());
            save(value, remaining.isNegative() || remaining.isZero() ? Duration.ofSeconds(1) : remaining);
            return Optional.of(value);
        }
    }

    private String key(String previewId) { return keyPrefix + previewId; }
}
