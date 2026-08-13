package com.aiassistant.channel;

import com.aiassistant.channel.model.InboundMessage;

public interface ChannelSessionStore {
    Long resolve(InboundMessage message, String workorderUserId);
}
