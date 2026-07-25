package com.aiassistant.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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

        log.info("触发上下文压缩: {} 条消息, 约 {} tokens", messages.size(), estimatedTokens);

        List<ChatMessage> recentMessages = new ArrayList<>();
        List<ChatMessage> oldMessages = new ArrayList<>();

        if (messages.size() <= RECENT_MESSAGES_TO_KEEP) {
            return messages;
        }

        for (int i = 0; i < messages.size(); i++) {
            if (i >= messages.size() - RECENT_MESSAGES_TO_KEEP) {
                recentMessages.add(messages.get(i));
            } else {
                oldMessages.add(messages.get(i));
            }
        }

        String summary = summarizeMessages(oldMessages);
        
        List<ChatMessage> compressedMessages = new ArrayList<>();
        compressedMessages.add(SystemMessage.from("[历史对话摘要]\n" + summary));
        compressedMessages.addAll(recentMessages);

        log.info("压缩完成: {} 条消息 -> {} 条消息", messages.size(), compressedMessages.size());
        
        return compressedMessages;
    }

    private String summarizeMessages(List<ChatMessage> messages) {
        StringBuilder messageText = new StringBuilder();
        messageText.append("请总结以下对话内容，提取关键信息和用户意图：\n\n");
        
        for (ChatMessage message : messages) {
            String role = message instanceof UserMessage ? "用户" : 
                          message instanceof AiMessage ? "助手" : "系统";
            messageText.append(role).append(": ").append(getMessageText(message)).append("\n\n");
        }

        try {
            List<ChatMessage> summaryMessages = new ArrayList<>();
            summaryMessages.add(SystemMessage.from("你是一个对话摘要助手，请用简洁的语言总结对话内容。"));
            summaryMessages.add(UserMessage.from(messageText.toString()));

            dev.langchain4j.model.chat.response.ChatResponse response = chatModel.chat(summaryMessages);
            return response.aiMessage().text();
        } catch (Exception e) {
            log.error("生成对话摘要失败，使用简单摘要", e);
            return generateSimpleSummary(messages);
        }
    }

    private String generateSimpleSummary(List<ChatMessage> messages) {
        StringBuilder summary = new StringBuilder();
        
        int userCount = 0;
        int assistantCount = 0;
        
        for (ChatMessage message : messages) {
            if (message instanceof UserMessage) {
                userCount++;
            } else if (message instanceof AiMessage) {
                assistantCount++;
            }
        }
        
        summary.append(String.format("对话包含 %d 条用户消息和 %d 条助手消息。", userCount, assistantCount));
        
        if (!messages.isEmpty()) {
            ChatMessage firstMessage = messages.get(0);
            ChatMessage lastMessage = messages.get(messages.size() - 1);
            
            if (firstMessage instanceof UserMessage) {
                String firstText = getMessageText(firstMessage);
                if (firstText.length() > 50) {
                    firstText = firstText.substring(0, 50) + "...";
                }
                summary.append(" 用户最初询问: ").append(firstText);
            }
            
            if (lastMessage instanceof AiMessage) {
                String lastText = getMessageText(lastMessage);
                if (lastText.length() > 50) {
                    lastText = lastText.substring(0, 50) + "...";
                }
                summary.append(" 助手最后回复: ").append(lastText);
            }
        }
        
        return summary.toString();
    }

    private String getMessageText(ChatMessage message) {
        if (message instanceof UserMessage) {
            return message.toString();
        } else if (message instanceof AiMessage) {
            return ((AiMessage) message).text();
        } else if (message instanceof SystemMessage) {
            return message.toString();
        }
        return message.toString();
    }

    private int estimateTokenCount(List<ChatMessage> messages) {
        int totalLength = 0;
        for (ChatMessage message : messages) {
            totalLength += getMessageText(message).length();
        }
        return totalLength / 4;
    }
}