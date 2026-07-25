package com.aiassistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LongTermMemoryService {

    @Value("${workorder.memory.base-path:.memory}")
    private String basePath;

    private static final String MEMORY_INDEX_FILE = "MEMORY.md";
    private static final String MEMORY_FILE_PREFIX = "memory_";
    private static final String MEMORY_FILE_SUFFIX = ".json";
    private static final int MAX_MEMORY_FILES_BEFORE_MERGE = 10;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public void initializeMemoryDirectory() {
        try {
            Path dir = Paths.get(basePath);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            Path indexFile = dir.resolve(MEMORY_INDEX_FILE);
            if (!Files.exists(indexFile)) {
                Files.writeString(indexFile, "# Memory Index\n\n", StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.error("初始化记忆目录失败", e);
        }
    }

    public List<MemoryEntry> loadMemories(Long userId) {
        List<MemoryEntry> memories = new ArrayList<>();
        
        try {
            Path indexFile = Paths.get(basePath).resolve(MEMORY_INDEX_FILE);
            if (!Files.exists(indexFile)) {
                return memories;
            }

            String indexContent = Files.readString(indexFile, StandardCharsets.UTF_8);
            List<String> memoryFileNames = parseIndexFile(indexContent, userId);

            for (String fileName : memoryFileNames) {
                Path memoryFile = Paths.get(basePath).resolve(fileName);
                if (Files.exists(memoryFile)) {
                    String content = Files.readString(memoryFile, StandardCharsets.UTF_8);
                    try {
                        if (content.trim().startsWith("[")) {
                            MemoryEntry[] entries = objectMapper.readValue(content, MemoryEntry[].class);
                            memories.addAll(Arrays.asList(entries));
                        } else {
                            MemoryEntry entry = objectMapper.readValue(content, MemoryEntry.class);
                            memories.add(entry);
                        }
                    } catch (JsonProcessingException e) {
                        log.warn("解析记忆文件失败: {}", fileName);
                    }
                }
            }

            memories.sort(Comparator.comparing(MemoryEntry::getCreatedAt).reversed());
        } catch (IOException e) {
            log.error("加载记忆失败", e);
        }

        return memories;
    }

    public void extractMemories(Long userId, String conversationContent) {
        List<MemoryEntry> newMemories = parseConversationForMemories(userId, conversationContent);
        
        for (MemoryEntry memory : newMemories) {
            saveMemory(memory);
        }

        checkAndMergeMemories();
    }

    private List<MemoryEntry> parseConversationForMemories(Long userId, String conversation) {
        List<MemoryEntry> memories = new ArrayList<>();
        
        String[] lines = conversation.split("\n");
        StringBuilder currentTopic = new StringBuilder();
        
        for (String line : lines) {
            if (line.contains("偏好") || line.contains("喜欢") || line.contains("习惯") || 
                line.contains("需求") || line.contains("目标")) {
                MemoryEntry preference = new MemoryEntry();
                preference.setUserId(userId);
                preference.setType("preference");
                preference.setContent(line.trim());
                preference.setCreatedAt(LocalDateTime.now());
                memories.add(preference);
            }
            
            if (line.contains("工单") || line.contains("任务") || line.contains("问题")) {
                currentTopic.append(line).append("\n");
            }
        }
        
        if (currentTopic.length() > 0) {
            MemoryEntry topic = new MemoryEntry();
            topic.setUserId(userId);
            topic.setType("topic");
            topic.setContent(currentTopic.toString().trim());
            topic.setCreatedAt(LocalDateTime.now());
            memories.add(topic);
        }

        return memories;
    }

    private void saveMemory(MemoryEntry memory) {
        try {
            String fileName = MEMORY_FILE_PREFIX + LocalDateTime.now().format(DATE_FORMATTER) + "_" + 
                    memory.getUserId() + MEMORY_FILE_SUFFIX;
            Path memoryFile = Paths.get(basePath).resolve(fileName);
            
            String content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(memory);
            Files.writeString(memoryFile, content, StandardCharsets.UTF_8);

            updateIndexFile(fileName, memory);
        } catch (IOException e) {
            log.error("保存记忆失败", e);
        }
    }

    private void updateIndexFile(String fileName, MemoryEntry memory) {
        try {
            Path indexFile = Paths.get(basePath).resolve(MEMORY_INDEX_FILE);
            String indexContent = Files.readString(indexFile, StandardCharsets.UTF_8);
            
            String entryLine = String.format("| %s | %s | %s | %s |\n", 
                    fileName, memory.getUserId(), memory.getType(), 
                    memory.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            if (!indexContent.contains("| Filename | UserId | Type | CreatedAt |")) {
                indexContent = "# Memory Index\n\n| Filename | UserId | Type | CreatedAt |\n|----------|--------|------|-----------|\n";
            }
            
            if (!indexContent.contains(fileName)) {
                indexContent = indexContent + entryLine;
                Files.writeString(indexFile, indexContent, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.error("更新索引文件失败", e);
        }
    }

    private List<String> parseIndexFile(String content, Long userId) {
        List<String> fileNames = new ArrayList<>();
        
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.contains("|") && !line.startsWith("#") && !line.contains("Filename")) {
                String[] parts = line.split("\\|");
                if (parts.length >= 3) {
                    String fileName = parts[1].trim();
                    String userIdStr = parts[2].trim();
                    if (userId == null || userId.toString().equals(userIdStr)) {
                        fileNames.add(fileName);
                    }
                }
            }
        }
        
        return fileNames;
    }

    private void checkAndMergeMemories() {
        try {
            Path dir = Paths.get(basePath);
            List<Path> memoryFiles = Files.list(dir)
                    .filter(p -> p.getFileName().toString().startsWith(MEMORY_FILE_PREFIX)
                            && p.getFileName().toString().endsWith(MEMORY_FILE_SUFFIX))
                    .collect(Collectors.toList());

            if (memoryFiles.size() >= MAX_MEMORY_FILES_BEFORE_MERGE) {
                mergeMemoryFiles(memoryFiles);
            }
        } catch (IOException e) {
            log.error("检查记忆文件合并失败", e);
        }
    }

    private void mergeMemoryFiles(List<Path> memoryFiles) {
        try {
            List<MemoryEntry> allEntries = new ArrayList<>();
            
            for (Path file : memoryFiles) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                try {
                    if (content.trim().startsWith("[")) {
                        MemoryEntry[] entries = objectMapper.readValue(content, MemoryEntry[].class);
                        allEntries.addAll(Arrays.asList(entries));
                    } else {
                        MemoryEntry entry = objectMapper.readValue(content, MemoryEntry.class);
                        allEntries.add(entry);
                    }
                } catch (JsonProcessingException e) {
                    log.warn("解析记忆文件失败: {}", file.getFileName());
                }
            }

            Map<String, MemoryEntry> deduplicated = new LinkedHashMap<>();
            for (MemoryEntry entry : allEntries) {
                String key = entry.getUserId() + "_" + entry.getType() + "_" + entry.getContent().hashCode();
                deduplicated.put(key, entry);
            }

            String mergedFileName = MEMORY_FILE_PREFIX + "merged_" + LocalDateTime.now().format(DATE_FORMATTER) + MEMORY_FILE_SUFFIX;
            Path mergedFile = Paths.get(basePath).resolve(mergedFileName);
            
            List<MemoryEntry> mergedList = new ArrayList<>(deduplicated.values());
            String content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mergedList);
            Files.writeString(mergedFile, content, StandardCharsets.UTF_8);

            for (Path file : memoryFiles) {
                Files.delete(file);
            }

            updateIndexAfterMerge(memoryFiles, mergedFileName);
        } catch (IOException e) {
            log.error("合并记忆文件失败", e);
        }
    }

    private void updateIndexAfterMerge(List<Path> deletedFiles, String mergedFileName) {
        try {
            Path indexFile = Paths.get(basePath).resolve(MEMORY_INDEX_FILE);
            String content = Files.readString(indexFile, StandardCharsets.UTF_8);

            for (Path file : deletedFiles) {
                content = content.replaceAll("\\|\\s*" + file.getFileName() + "\\s*\\|", "");
            }

            String mergedEntry = String.format("| %s | merged | merged | %s |\n", 
                    mergedFileName, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            content = content + mergedEntry;
            
            Files.writeString(indexFile, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("更新合并后的索引失败", e);
        }
    }

    public String getMemorySummary(Long userId) {
        List<MemoryEntry> memories = loadMemories(userId);
        
        StringBuilder summary = new StringBuilder();
        summary.append("用户记忆摘要:\n");
        
        Map<String, List<String>> groupedByType = new HashMap<>();
        for (MemoryEntry entry : memories) {
            groupedByType.computeIfAbsent(entry.getType(), k -> new ArrayList<>())
                    .add(entry.getContent());
        }
        
        for (Map.Entry<String, List<String>> entry : groupedByType.entrySet()) {
            summary.append(String.format("\n【%s】\n", entry.getKey()));
            for (String content : entry.getValue()) {
                summary.append("- ").append(content).append("\n");
            }
        }
        
        return summary.toString();
    }

    @Data
    public static class MemoryEntry {
        private Long userId;
        private String type;
        private String content;
        private LocalDateTime createdAt;
    }
}