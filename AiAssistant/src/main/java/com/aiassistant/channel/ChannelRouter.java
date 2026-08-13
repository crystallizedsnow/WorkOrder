package com.aiassistant.channel;

import com.aiassistant.channel.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChannelRouter {
    /** Backend 生成格式：UUID + '.' + 24位 Base64URL 随机串。 */
    private static final Pattern FEISHU_BINDING_CODE = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.[A-Za-z0-9_-]{24}$");
    private final MessageDeduplicator deduplicator;
    private final ChannelSessionStore sessions;
    private final FeishuIdentityClient identities;
    private final AgentGateway agentGateway;
    private final ConversationExecutor executor;
    private final ChannelRateLimiter rateLimiter;
    private final List<ChannelAdapter> adapters;

    public RouteResult route(InboundMessage message) {
        if (!deduplicator.claim(message.channel().name(), message.messageId())) return RouteResult.DUPLICATE;
        if (!rateLimiter.allow(message.tenantId() + ':' + message.senderId())) {
            send(message, "请求过于频繁，请稍后再试。");
            return RouteResult.RATE_LIMITED;
        }
        if (message.channel() != ChannelType.FEISHU) throw new IllegalArgumentException("Async router only accepts external channels");
        if (!"text".equals(message.messageType())) {
            send(message, "暂时只支持文本消息。");
            return RouteResult.UNSUPPORTED;
        }
        executor.submit(message.tenantId() + ':' + message.botAccountId() + ':' + message.senderId(), () -> processFeishu(message));
        return RouteResult.ACCEPTED;
    }

    private void processFeishu(InboundMessage message) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("traceId", message.traceId())) {
            FeishuIdentityClient.BindingResult binding = identities.find(message.tenantId(), message.senderUnionId(), message.senderId());
            if (!binding.bound()) {
                if (isBindingCode(message.text())) {
                    binding = identities.confirm(message.text(), message.tenantId(), message.senderUnionId(), message.senderId());
                }
                if (!binding.bound()) {
                    send(message, "尚未绑定工单账号。请先在 workorder-cli 执行 channel bind-code，再把绑定码发给我。");
                    return;
                }
                send(message, "绑定成功：" + nullSafe(binding.maskedName()) + " " + nullSafe(binding.maskedStaffNumber()));
                return;
            }
            FeishuIdentityClient.ProxyToken token = identities.exchange(message.tenantId(), message.senderUnionId(), message.senderId(),
                    message.tenantId() + ':' + message.botAccountId() + ':' + message.senderId());
            Long sessionId = sessions.resolve(message, token.userId());
            String answer = agentGateway.execute(new AgentRequest(sessionId, token.userId(), message.text(), token.accessToken(),
                    message.channel(), message.tenantId(), message.senderId(), message.conversationId(), message.traceId())).collectList().map(parts -> String.join("", parts)).block();
            // 空回答表示 Agent 已发送交互卡片，不能再补发模型生成的确认文本。
            if (answer != null && !answer.isBlank()) {
                send(message, answer);
            }
        } catch (Exception ex) {
            log.error("Channel message processing failed: messageId={}, error={}", message.messageId(), ex.getMessage());
            send(message, "请求处理失败，请稍后重试。追踪号：" + message.traceId());
        }
    }

    private void send(InboundMessage inbound, String text) {
        adapters.stream().filter(a -> a.type() == inbound.channel()).findFirst().orElseThrow()
                .send(new OutboundMessage(inbound.conversationId(), inbound.messageId(), text, inbound.traceId()));
    }
    private String nullSafe(String value) { return value == null ? "" : value; }
    static boolean isBindingCode(String value) {
        return value != null && FEISHU_BINDING_CODE.matcher(value.trim()).matches();
    }
    public enum RouteResult { ACCEPTED, DUPLICATE, UNSUPPORTED, RATE_LIMITED }
}
