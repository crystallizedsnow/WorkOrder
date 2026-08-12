package com.aiassistant.agent;

import com.aiassistant.llm.ToolDefinition;
import com.aiassistant.llm.ChatMessage;
import com.aiassistant.llm.ToolCall;
import com.aiassistant.todo.TodoManager;
import com.aiassistant.todo.TodoWriteTools;
import com.aiassistant.tools.CliExecutorTools;
import com.aiassistant.tools.FileTools;
import com.aiassistant.tools.SsoCliTools;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolDispatcherTodoSchemaTest {

    @Test
    void todoSchemasHideSessionAndExposeSemanticParameters() {
        ToolDispatcher dispatcher = new ToolDispatcher();
        ReflectionTestUtils.setField(dispatcher, "cliExecutorTools", new CliExecutorTools());
        ReflectionTestUtils.setField(dispatcher, "fileTools", new FileTools());
        ReflectionTestUtils.setField(dispatcher, "ssoCliTools", new SsoCliTools());
        ReflectionTestUtils.setField(dispatcher, "todoWriteTools", new TodoWriteTools(new TodoManager()));
        dispatcher.initialize();

        List<ToolDefinition> definitions = dispatcher.getToolDefinitions();
        ToolDefinition todoWrite = find(definitions, "todoWrite");
        String schema = todoWrite.getFunction().getParameters().toString();

        assertFalse(schema.contains("sessionId"));
        assertTrue(schema.contains("content"));
        assertTrue(schema.contains("priority"));

        String updateSchema = find(definitions, "todoUpdate").getFunction().getParameters().toString();
        assertFalse(updateSchema.contains("sessionId"));
        assertTrue(updateSchema.contains("todoId"));
        assertTrue(updateSchema.contains("status"));
    }

    @Test
    void longTaskDetectionUsesGenericActionDependencies() {
        AgentLoop loop = new AgentLoop();

        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(loop, "isLongTask",
                "先创建报告，完成后查询结果，然后告诉我如何验证"));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(loop, "isLongTask", "查询一条记录"));
    }

    @Test
    void realExecutionOfLatestDryRunRequiresStructuredConfirmation() {
        AgentLoop loop = new AgentLoop();
        ToolCall dryRun = ToolCall.builder().function(ToolCall.FunctionCall.builder()
                .name("executeCliCommand")
                .arguments("{\"command\":\"workorder-cli --dry-run sample --id 1\"}")
                .build()).build();
        List<ChatMessage> messages = List.of(ChatMessage.assistantWithToolCalls("", List.of(dryRun)));

        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(loop, "isUnconfirmedDryRunExecution",
                messages, "executeCliCommand", "{\"command\":\"workorder-cli sample --id 1\"}", "继续"));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(loop, "isUnconfirmedDryRunExecution",
                messages, "executeCliCommand", "{\"command\":\"workorder-cli sample --id 1\"}",
                "{\"action\":\"confirm_execute\",\"target\":\"last_dry_run\",\"confirmed\":true}"));
    }

    private ToolDefinition find(List<ToolDefinition> definitions, String name) {
        return definitions.stream()
                .filter(definition -> name.equals(definition.getFunction().getName()))
                .findFirst()
                .orElseThrow();
    }
}
