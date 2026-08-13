package com.aiassistant.channel;

import com.aiassistant.channel.model.ChannelType;
import com.aiassistant.channel.model.InboundMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class FeishuMessageParser {
    private final ObjectMapper mapper = new ObjectMapper();

    public InboundMessage parse(P2MessageReceiveV1 payload) {
        var header = payload.getHeader(); var event = payload.getEvent(); var message = event.getMessage(); var sender = event.getSender();
        String type = message.getMessageType();
        String text = "";
        if ("text".equals(type)) {
            try { text = mapper.readTree(message.getContent()).path("text").asText(); }
            catch (Exception ex) { throw new IllegalArgumentException("Invalid Feishu text content", ex); }
        }
        String trace = header.getEventId() == null ? UUID.randomUUID().toString() : header.getEventId();
        return new InboundMessage(ChannelType.FEISHU, header.getAppId(), header.getTenantKey(), message.getChatId(),
                message.getMessageId(), sender.getSenderId().getOpenId(), sender.getSenderId().getUnionId(), text, type,
                trace, Instant.now(), Map.of("chatType", message.getChatType(), "senderType", sender.getSenderType()));
    }
}
