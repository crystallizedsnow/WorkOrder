package com.aiassistant.channel;

import com.aiassistant.channel.model.ChannelType;
import com.aiassistant.channel.model.OutboundMessage;

public interface ChannelAdapter {
    ChannelType type();
    void send(OutboundMessage message);
}
