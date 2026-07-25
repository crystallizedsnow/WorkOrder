package com.aiassistant.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class LongTermMemoryServiceTest {

    @Autowired
    private LongTermMemoryService longTermMemoryService;

    private final String basePath = ".memory";

    @BeforeEach
    public void setup() {
        longTermMemoryService.initializeMemoryDirectory();
    }

    @AfterEach
    public void cleanup() {
        try {
            Path dir = Paths.get(basePath);
            if (Files.exists(dir)) {
                Files.walk(dir)
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (Exception e) {
                                // ignore
                            }
                        });
            }
        } catch (Exception e) {
            // ignore cleanup errors
        }
    }

    @Test
    public void testExtractAndLoadMemories() {
        Long userId = 1L;
        String conversation = "用户: 我喜欢每页显示20条工单\n助手: 好的，我记住了您的偏好\n用户: 查询工单12345\n助手: 工单12345的状态是处理中";

        longTermMemoryService.extractMemories(userId, conversation);
        
        List<LongTermMemoryService.MemoryEntry> memories = longTermMemoryService.loadMemories(userId);
        
        assertNotNull(memories);
        assertTrue(memories.size() >= 1);
    }

    @Test
    public void testGetMemorySummary() {
        Long userId = 2L;
        String conversation = "用户: 我习惯使用excel格式导出\n用户: 帮我查一下任务ABC";

        longTermMemoryService.extractMemories(userId, conversation);
        
        String summary = longTermMemoryService.getMemorySummary(userId);
        
        assertNotNull(summary);
        assertTrue(summary.contains("用户记忆摘要"));
        assertTrue(summary.contains("preference") || summary.contains("topic"));
    }

    @Test
    public void testLoadEmptyMemories() {
        Long userId = 999L;
        
        List<LongTermMemoryService.MemoryEntry> memories = longTermMemoryService.loadMemories(userId);
        
        assertNotNull(memories);
        assertTrue(memories.isEmpty());
    }

    @Test
    public void testGetEmptyMemorySummary() {
        Long userId = 888L;
        
        String summary = longTermMemoryService.getMemorySummary(userId);
        
        assertNotNull(summary);
        assertTrue(summary.contains("用户记忆摘要"));
    }
}