package com.aiassistant.channel;

import com.aiassistant.channel.confirmation.WriteConfirmation;
import com.aiassistant.channel.confirmation.WriteConfirmationService;
import com.aiassistant.common.SessionContext;
import com.aiassistant.tools.CliExecutorTools;
import com.lark.oapi.event.cardcallback.P2CardActionTriggerHandler;
import com.lark.oapi.event.cardcallback.model.CallBackCard;
import com.lark.oapi.event.cardcallback.model.CallBackToast;
import com.lark.oapi.event.cardcallback.model.P2CardActionTrigger;
import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FeishuCardActionHandler extends P2CardActionTriggerHandler {
    private final WriteConfirmationService confirmations;
    private final FeishuIdentityClient identities;
    private final CliExecutorTools cli;
    private final FeishuChannelAdapter channel;

    public FeishuCardActionHandler(WriteConfirmationService confirmations, FeishuIdentityClient identities,
                                   CliExecutorTools cli, FeishuChannelAdapter channel) {
        this.confirmations = confirmations;
        this.identities = identities;
        this.cli = cli;
        this.channel = channel;
    }

    @Override
    public P2CardActionTriggerResponse handle(P2CardActionTrigger event) {
        var data = event.getEvent();
        Map<String, Object> value = data.getAction().getValue();
        String id = String.valueOf(value.get("operationId"));
        boolean confirm = "confirm".equals(value.get("action"));
        var operator = data.getOperator();
        var context = data.getContext();
        var decision = confirmations.claim(id, operator.getTenantKey(), context.getOpenChatId(), operator.getOpenId(), confirm);

        P2CardActionTriggerResponse response = new P2CardActionTriggerResponse();
        CallBackToast toast = new CallBackToast();
        toast.setType(decision.accepted() ? "success" : "warning");
        toast.setContent(decision.message());
        response.setToast(toast);

        if (!decision.accepted()) return response;
        WriteConfirmation confirmation = decision.confirmation();
        if (!confirm) {
            response.setCard(card(FeishuChannelAdapter.buildResultCard(confirmation, WriteConfirmation.Status.CANCELLED)));
            return response;
        }

        WriteConfirmation.Status status;
        String result;
        try {
            var token = identities.exchange(confirmation.getTenantId(), operator.getUnionId(), operator.getOpenId(),
                    confirmation.getSessionId().toString());
            SessionContext.setStaticToken(token.accessToken());
            result = cli.executeCliCommand(confirmation.getCommand());
            status = result.matches("(?s).*\\\"code\\\"\\s*:\\s*0.*")
                    ? WriteConfirmation.Status.SUCCEEDED : WriteConfirmation.Status.FAILED;
        } catch (Exception exception) {
            status = WriteConfirmation.Status.UNKNOWN;
            result = exception.getMessage();
        } finally {
            SessionContext.clearToken();
        }

        confirmations.complete(id, status, result);
        toast.setType(status == WriteConfirmation.Status.SUCCEEDED ? "success" : "warning");
        toast.setContent(status == WriteConfirmation.Status.SUCCEEDED ? "执行成功" : "执行失败，请查看会话结果");
        response.setCard(card(FeishuChannelAdapter.buildResultCard(confirmation, status)));
        channel.sendExecutionResult(confirmation, status, result);
        return response;
    }

    private CallBackCard card(Map<String, Object> data) {
        CallBackCard card = new CallBackCard();
        card.setType("raw");
        card.setData(data);
        return card;
    }
}
