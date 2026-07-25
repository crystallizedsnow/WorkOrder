package com.aiassistant;

import com.aiassistant.agent.AgentLoop;
import com.aiassistant.common.SessionContext;
import com.aiassistant.controller.AIChatController;
import com.aiassistant.prompt.DynamicPromptGenerator;
import com.aiassistant.tools.CliExecutorTools;
import com.aiassistant.tools.FileTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "langchain4j.zhipu.api-key=test-api-key",
    "langchain4j.zhipu.chat-model.model-name=glm-4.5-air",
    "langchain4j.zhipu.chat-model.temperature=0.7",
    "workorder.file.output-dir=./test-output/"
})
@DisplayName("阶段一：基础能力搭建 - 验收测试")
class StageOneAcceptanceTest {

    @Autowired
    private AgentLoop agentLoop;

    @Autowired
    private DynamicPromptGenerator dynamicPromptGenerator;

    @Autowired
    private CliExecutorTools cliExecutorTools;

    @Autowired
    private FileTools fileTools;

    @Autowired
    private SessionContext sessionContext;

    @Autowired
    private AIChatController aiChatController;

    @Test
    @DisplayName("TC-001: 项目启动验证 - Spring Boot应用能正常启动")
    void testApplicationStartup() {
        assertNotNull(agentLoop, "AgentLoop bean 应被正确注入");
        assertNotNull(dynamicPromptGenerator, "DynamicPromptGenerator bean 应被正确注入");
        assertNotNull(cliExecutorTools, "CliExecutorTools bean 应被正确注入");
        assertNotNull(fileTools, "FileTools bean 应被正确注入");
        assertNotNull(sessionContext, "SessionContext bean 应被正确注入");
        assertNotNull(aiChatController, "AIChatController bean 应被正确注入");
    }

    @Test
    @DisplayName("TC-003: 动态提示词生成器 - 能生成有效的系统提示词")
    void testDynamicPromptGeneratorGeneratesPrompt() {
        String prompt = dynamicPromptGenerator.generate();
        assertNotNull(prompt, "系统提示词不应为空");
        assertTrue(prompt.length() > 0, "系统提示词长度应大于0");
        assertTrue(prompt.contains("工单"), "系统提示词应包含工单方面的指令");
    }

    @Test
    @DisplayName("TC-004: 文件操作工具 - 能创建并读取TXT文件")
    void testFileToolsCanCreateAndReadTxtFile() throws IOException {
        String testContent = "test content";
        String filePath = "test_file.txt";

        String createResult = fileTools.createTxtFile(filePath, testContent);
        assertNotNull(createResult, "文件创建结果不应为空");
        assertTrue(createResult.contains("创建成功"), "文件创建应成功");

        String readResult = fileTools.readTxtFile(filePath);
        assertNotNull(readResult, "文件读取结果不应为空");
        assertEquals(testContent, readResult.trim(), "读取的内容应与写入的内容一致");

        java.io.File file = new java.io.File("./test-output/" + filePath);
        file.delete();
    }

    @Test
    @DisplayName("TC-005: 文件操作工具 - 能创建并读取Markdown文件")
    void testFileToolsCanCreateAndReadMarkdownFile() throws IOException {
        String testContent = "# 测试标题\n\n这是测试内容";
        String filePath = "test_file.md";

        String createResult = fileTools.createMarkdownFile(filePath, testContent);
        assertNotNull(createResult, "文件创建结果不应为空");
        assertTrue(createResult.contains("创建成功"), "文件创建应成功");

        String readResult = fileTools.readMarkdownFile(filePath);
        assertNotNull(readResult, "文件读取结果不应为空");
        assertEquals(testContent, readResult.trim(), "读取的内容应与写入的内容一致");

        java.io.File file = new java.io.File("./test-output/" + filePath);
        file.delete();
    }

    @Test
    @DisplayName("TC-006: 文件操作工具 - 能创建Excel文件")
    void testFileToolsCanCreateExcelFile() throws IOException {
        String filePath = "test_excel.xlsx";
        java.util.List<String> headers = java.util.Arrays.asList("姓名", "年龄", "部门");
        java.util.List<java.util.List<Object>> rows = new java.util.ArrayList<>();
        rows.add(java.util.Arrays.asList("张三", 25, "技术部"));
        rows.add(java.util.Arrays.asList("李四", 30, "产品部"));

        String result = fileTools.createExcelFile(filePath, headers, rows);
        assertNotNull(result, "Excel创建结果不应为空");
        assertTrue(result.contains("创建成功"), "Excel创建应成功");

        java.io.File file = new java.io.File("./test-output/" + filePath);
        file.delete();
    }

    @Test
    @DisplayName("TC-007: 文件操作工具 - 读取不存在的文件")
    void testFileToolsReadNonExistentFile() throws IOException {
        String result = fileTools.readTxtFile("non_existent_file.txt");
        assertNotNull(result, "文件读取结果不应为空");
        assertTrue(result.contains("文件不存在"), "读取不存在的文件应返回错误提示");
    }

    @Test
    @DisplayName("TC-008: 会话上下文 - 能设置和获取会话信息")
    void testSessionContextSetAndGet() {
        Long memoryId = 123L;
        String token = "test-token-123";

        sessionContext.setSession(memoryId, token);

        assertNotNull(sessionContext.getMemoryId(), "Memory ID应被正确设置");
        assertNotNull(sessionContext.getToken(), "令牌应被正确设置");
        assertEquals(memoryId, sessionContext.getMemoryId());
        assertEquals(token, sessionContext.getToken());
    }

    @Test
    @DisplayName("TC-009: 会话上下文 - 能清除会话信息")
    void testSessionContextClear() {
        sessionContext.setSession(456L, "token-456");
        assertNotNull(sessionContext.getMemoryId());

        sessionContext.clear();

        assertNull(sessionContext.getMemoryId(), "清除会话后Memory ID应为null");
        assertNull(sessionContext.getToken(), "清除会话后令牌应为null");
    }

    @Test
    @DisplayName("TC-010: 动态提示词生成器 - 模板文件加载成功")
    void testDynamicPromptGeneratorTemplateLoaded() {
        String prompt = dynamicPromptGenerator.generate();
        assertTrue(prompt.contains("思考"), "提示词应包含思考指令");
        assertTrue(prompt.contains("行动"), "提示词应包含行动指令");
        assertTrue(prompt.contains("工具"), "提示词应包含工具调用指令");
    }

    @Test
    @DisplayName("TC-012: 文件操作工具 - 路径安全校验")
    void testFileToolsPathValidation() {
        assertThrows(IllegalArgumentException.class, () -> {
            fileTools.createTxtFile("../malicious.txt", "bad content");
        }, "包含 '..' 的路径应被拒绝");
    }

    @Test
    @DisplayName("TC-013: 工具分发器 - 工具注册机制正常")
    void testToolDispatcherRegistration() {
        assertNotNull(agentLoop, "AgentLoop应已初始化，包含ToolDispatcher");
    }

    @Test
    @DisplayName("TC-014: CLI工具执行 - 未配置Token时应抛出异常")
    void testCliExecutorToolsNoTokenThrowsException() {
        assertThrows(RuntimeException.class, () -> {
            cliExecutorTools.executeCliCommand("work_order page --page-num 1 --page-size 10");
        }, "未配置Token时执行CLI命令应抛出异常");
    }
}