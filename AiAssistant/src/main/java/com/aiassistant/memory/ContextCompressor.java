package com.aiassistant.memory;

import com.aiassistant.llm.ChatMessage;
import com.aiassistant.llm.ChatModel;
import com.aiassistant.llm.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 上下文压缩器：当消息数/token 超阈值时，用 LLM 对历史消息做摘要，保留最近若干条。
 * <p>
 * 使用自定义 {@link ChatMessage} 与 {@link ChatModel}，不再依赖 langchain4j。
 */
@Component
@Slf4j
public class ContextCompressor {

    @Autowired
    private ChatModel chatModel;

    private static final int MAX_MESSAGES_BEFORE_COMPRESSION = 20;
    private static final int RECENT_MESSAGES_TO_KEEP = 10;
    private static final int MAX_TOKENS_BEFORE_COMPRESSION = 8000;

    public List<ChatMessage> compress(List<ChatMessage> messages) {
        if (messages == null || messages.size() <= MAX_MESSAGES_BEFORE_COMPRESSION) {
            return messages;
        }
        int estimatedTokens = estimateTokenCount(messages);
        if (estimatedTokens <= MAX_TOKENS_BEFORE_COMPRESSION) {
            return messages;
        }
        if (messages.size() <= RECENT_MESSAGES_TO_KEEP) {
            return messages;
        }

        log.info("触发上下文压缩: {} 条消息, 约 {} tokens", messages.size(), estimatedTokens);

        List<ChatMessage> recentMessages = new ArrayList<>();
        List<ChatMessage> oldMessages = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            if (i >= messages.size() - RECENT_MESSAGES_TO_KEEP) {
                recentMessages.add(messages.get(i));
            } else {
                oldMessages.add(messages.get(i));
            }
        }

        String summary = summarizeMessages(oldMessages);

        List<ChatMessage> compressedMessages = new ArrayList<>();
        compressedMessages.add(ChatMessage.system("[历史对话摘要]\n" + summary));
        compressedMessages.addAll(recentMessages);

        log.info("压缩完成: {} 条消息 -> {} 条消息", messages.size(), compressedMessages.size());
        return compressedMessages;
    }

    private String summarizeMessages(List<ChatMessage> messages) {
        StringBuilder messageText = new StringBuilder();
        messageText.append("请总结以下对话内容，提取关键信息和用户意图：\n\n");
        for (ChatMessage message : messages) {
            String role = "user".equals(message.getRole()) ? "用户"
                    : "assistant".equals(message.getRole()) ? "助手" : "系统";
            messageText.append(role).append(": ").append(getMessageText(message)).append("\n\n");
        }
        try {
            List<ChatMessage> summaryMessages = new ArrayList<>();
            summaryMessages.add(ChatMessage.system("你是一个对话摘要助手，请用简洁的语言总结对话内容。"));
            summaryMessages.add(ChatMessage.user(messageText.toString()));
            ChatResponse response = chatModel.chat(summaryMessages, Collections.emptyList());
            return response.getContent();
        } catch (Exception e) {
            log.error("生成对话摘要失败，使用简单摘要: {}", e.getMessage());
            return generateSimpleSummary(messages);
        }
    }

    private String generateSimpleSummary(List<ChatMessage> messages) {
        StringBuilder summary = new StringBuilder();
        int userCount = 0;
        int assistantCount = 0;
        for (ChatMessage message : messages) {
            if ("user".equals(message.getRole())) {
                userCount++;
            } else if ("assistant".equals(message.getRole())) {
                assistantCount++;
            }
        }
        summary.append(String.format("对话包含 %d 条用户消息和 %d 条助手消息。", userCount, assistantCount));
        if (!messages.isEmpty()) {
            String firstText = getMessageText(messages.get(0));
            if (firstText.length() > 50) {
                firstText = firstText.substring(0, 50) + "...";
            }
            summary.append(" 用户最初询问: ").append(firstText);
        }
        return summary.toString();
    }

    private String getMessageText(ChatMessage message) {
        return message.getContent() == null ? "" : message.getContent();
    }

    private int estimateTokenCount(List<ChatMessage> messages) {
        int totalLength = 0;
        for (ChatMessage message : messages) {
            totalLength += getMessageText(message).length();
        }
        return totalLength / 4;
    }
}
