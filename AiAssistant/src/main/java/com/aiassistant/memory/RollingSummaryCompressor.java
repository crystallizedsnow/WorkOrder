package com.aiassistant.memory;

import com.aiassistant.llm.ChatMessage;
import com.aiassistant.llm.ChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class RollingSummaryCompressor {
    private static final Pattern WORK_ORDER_CODE = Pattern.compile("WO\\d{12,}");
    private final ChatModel chatModel;

    public String compress(String previous, List<MessageUnit> units) {
        StringBuilder source = new StringBuilder();
        if (previous != null && !previous.isBlank()) source.append("旧摘要：\n").append(previous).append("\n\n");
        source.append("新增历史：\n");
        for (MessageUnit unit : units) for (ChatMessage message : unit.chatMessages()) {
            if (isDiscoveryProtocolMessage(message)) continue;
            source.append(message.getRole()).append(": ").append(message.getContent() == null ? "" : message.getContent()).append('\n');
            if (message.getToolCalls() != null) message.getToolCalls().forEach(call -> source.append("tool_call: ")
                    .append(call.getFunction() == null ? "" : call.getFunction().getName()).append('\n'));
        }
        List<ChatMessage> prompt = new ArrayList<>();
        prompt.add(ChatMessage.system("生成会话内短期记忆摘要。仅输出以下固定章节：当前目标、明确约束、已完成事项、关键业务标识、未完成事项、失败与待补充。不得保存密码、Token、密钥、历史写操作授权或把历史业务状态表述为当前事实。工单编号等业务标识必须从输入逐字复制，禁止改写、补全或虚构。内容必须简洁。"));
        prompt.add(ChatMessage.user(source.toString()));
        String result = chatModel.chat(prompt, List.of()).getContent();
        if (result == null || result.isBlank() || containsCredential(result)
                || !identifiers(result).stream().allMatch(identifiers(source.toString())::contains)) {
            throw new IllegalStateException("Invalid memory summary");
        }
        return result.trim();
    }

    private boolean isDiscoveryProtocolMessage(ChatMessage message) {
        if (message == null) return true;
        if ("tool".equals(message.getRole())) {
            return Set.of("listCliCommands", "getCliCommandSchema", "getSkillContent").contains(message.getName());
        }
        if ("assistant".equals(message.getRole()) && message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            return message.getToolCalls().stream().allMatch(call -> call.getFunction() != null
                    && Set.of("listCliCommands", "getCliCommandSchema", "getSkillContent")
                    .contains(call.getFunction().getName()));
        }
        return false;
    }

    private Set<String> identifiers(String value) {
        Set<String> result = new HashSet<>();
        Matcher matcher = WORK_ORDER_CODE.matcher(value == null ? "" : value);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }

    private boolean containsCredential(String value) {
        String normalized = value.toLowerCase();
        return normalized.contains("bearer ") || normalized.contains("--password") || normalized.contains("\"password\"")
                || normalized.contains("api-key") || normalized.contains("api_key");
    }
}
