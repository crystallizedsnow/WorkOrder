package com.aiassistant.channel;

public interface MessageDeduplicator {
    boolean claim(String channel, String messageId);
}
