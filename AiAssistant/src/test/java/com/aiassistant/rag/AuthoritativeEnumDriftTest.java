package com.aiassistant.rag;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** CI 门禁：权威枚举文档不得与后端枚举事实源漂移。 */
class AuthoritativeEnumDriftTest {
    private static final Pattern ENUM_VALUE = Pattern.compile("[A-Z_]+\\((\\d+),\\s*\"([^\"]+)\"\\)");

    @Test
    void knowledgeSnapshotMatchesBackendEnums() throws Exception {
        Path repository = Path.of("..").toAbsolutePath().normalize();
        String knowledge = Files.readString(Path.of("src/main/resources/knowledge-base/workorder-enums.md"),
                StandardCharsets.UTF_8);
        assertEnumDocument(repository.resolve("backend/workorder-server/src/main/java/com/example/spring_vue_demo/enums/WorkOrderStatusEnum.java"), knowledge);
        assertEnumDocument(repository.resolve("backend/workorder-server/src/main/java/com/example/spring_vue_demo/enums/WorkOrderTypeEnum.java"), knowledge);
        assertEnumDocument(repository.resolve("backend/workorder-server/src/main/java/com/example/spring_vue_demo/enums/WorkOrderPriorityLevelEnum.java"), knowledge);
        assertEnumDocument(repository.resolve("backend/workorder-server/src/main/java/com/example/spring_vue_demo/enums/HandleTypeEnum.java"), knowledge);
    }

    private void assertEnumDocument(Path source, String knowledge) throws Exception {
        assertTrue(Files.exists(source), "后端枚举事实源不存在: " + source);
        Matcher matcher = ENUM_VALUE.matcher(Files.readString(source, StandardCharsets.UTF_8));
        int values = 0;
        while (matcher.find()) {
            values++;
            String tableValue = "| " + matcher.group(1) + " | " + matcher.group(2) + " |";
            assertTrue(knowledge.contains(tableValue), () -> "知识库与后端枚举漂移，缺少: " + tableValue);
        }
        assertTrue(values > 0, "未从后端枚举解析出任何值: " + source);
    }
}
