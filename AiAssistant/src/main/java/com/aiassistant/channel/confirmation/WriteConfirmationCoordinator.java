package com.aiassistant.channel.confirmation;

import com.aiassistant.channel.model.AgentRequest;
import com.aiassistant.common.SessionContext;
import com.aiassistant.tools.CliExecutorTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class WriteConfirmationCoordinator {
    private final WriteConfirmationService confirmations;
    private final CliPreviewClient previews;
    private final CliExecutorTools cli;
    private final ObjectMapper mapper;

    public Optional<String> handle(AgentRequest request) {
        DecisionInput input = parse(request.query());
        if (input == null) return Optional.empty();
        WriteConfirmationService.Decision decision = confirmations.claimWeb(request.sessionId(), request.userId(), input.confirm());
        if (!decision.accepted()) return Optional.of(decision.message());
        WriteConfirmation pending = decision.confirmation();
        if (!input.confirm()) {
            try { previews.decide(pending.getPreviewId(), request.accessToken(), false); }
            catch (Exception ignored) { /* 本地状态已取消；服务端记录会按 TTL 过期。 */ }
            return Optional.of("已取消本次预演，不会执行真实写操作。");
        }
        try {
            previews.decide(pending.getPreviewId(), request.accessToken(), true);
            SessionContext.setStaticToken(request.accessToken());
            String result = cli.executeCliCommand(pending.getCommand());
            WriteConfirmation.Status status = result.matches("(?s).*\\\"code\\\"\\s*:\\s*[01].*")
                    ? WriteConfirmation.Status.SUCCEEDED : WriteConfirmation.Status.FAILED;
            confirmations.complete(pending.getOperationId(), status, result);
            return Optional.of(result);
        } catch (Exception error) {
            confirmations.complete(pending.getOperationId(), WriteConfirmation.Status.UNKNOWN, error.getMessage());
            return Optional.of("执行失败：" + error.getMessage());
        } finally {
            SessionContext.clearToken();
        }
    }

    private DecisionInput parse(String text) {
        try {
            JsonNode node = mapper.readTree(text);
            String action = node.path("action").asText();
            if (!"last_dry_run".equals(node.path("target").asText())) return null;
            if ("confirm_execute".equals(action) && node.path("confirmed").asBoolean(false)) return new DecisionInput(true);
            if ("cancel_execute".equals(action) && !node.path("confirmed").asBoolean(true)) return new DecisionInput(false);
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private record DecisionInput(boolean confirm) {}
}
