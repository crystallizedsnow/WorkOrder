package com.aiassistant.store;

import com.aiassistant.common.ChatMessages;
import com.aiassistant.llm.ChatMessage;
import com.aiassistant.memory.ChatMemoryStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * 基于 MongoDB 的聊天记忆存储。
 * <p>
 * 使用 Jackson 序列化自定义 {@link ChatMessage} 列表为 JSON 字符串存入 content 字段。
 * 替代 langchain4j 的 ChatMessageSerializer。
 *
 * @author wtt
 * @date 2026/02/27
 */
@Component
@Slf4j
public class MongoChatMemoryStore implements ChatMemoryStore {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        Criteria criteria = Criteria.where("memoryId").is(memoryId);
        Query query = new Query(criteria);
        ChatMessages chatMessages = mongoTemplate.findOne(query, ChatMessages.class);
        if (chatMessages == null || chatMessages.getContent() == null || chatMessages.getContent().isEmpty()) {
            return new LinkedList<>();
        }
        try {
            List<ChatMessage> messages = OBJECT_MAPPER.readValue(
                    chatMessages.getContent(), new TypeReference<List<ChatMessage>>() {});
            return new LinkedList<>(messages);
        } catch (Exception e) {
            log.warn("反序列化聊天记录失败，返回空列表: memoryId={} - {}", memoryId, e.getMessage());
            return new LinkedList<>();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> list) {
        try {
            String content = OBJECT_MAPPER.writeValueAsString(list == null ? Collections.emptyList() : list);
            Criteria criteria = Criteria.where("memoryId").is(memoryId);
            Query query = new Query(criteria);
            Update update = new Update();
            update.set("content", content);
            mongoTemplate.upsert(query, update, ChatMessages.class);
        } catch (Exception e) {
            log.error("序列化聊天记录失败: memoryId={} - {}", memoryId, e.getMessage());
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        Criteria criteria = Criteria.where("memoryId").is(memoryId);
        Query query = new Query(criteria);
        mongoTemplate.remove(query, ChatMessages.class);
    }
}
