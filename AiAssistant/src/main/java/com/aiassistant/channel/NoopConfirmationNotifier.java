package com.aiassistant.channel;
import com.aiassistant.channel.confirmation.WriteConfirmation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
@Component
public class NoopConfirmationNotifier implements ConfirmationNotifier {
    private final ObjectProvider<FeishuChannelAdapter> adapter;
    public NoopConfirmationNotifier(ObjectProvider<FeishuChannelAdapter> adapter) { this.adapter = adapter; }
    public void notify(WriteConfirmation confirmation) {
        FeishuChannelAdapter feishu = adapter.getIfAvailable();
        if (feishu != null) feishu.sendConfirmation(confirmation);
    }
}
