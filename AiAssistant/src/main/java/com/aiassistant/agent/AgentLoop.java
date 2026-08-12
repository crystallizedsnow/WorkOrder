package com.aiassistant.agent;

import com.aiassistant.common.SessionContext;
import com.aiassistant.hook.HookContext;
import com.aiassistant.hook.HookRegistry;
import com.aiassistant.hook.HookType;
import com.aiassistant.llm.ChatMessage;
import com.aiassistant.llm.ChatModel;
import com.aiassistant.llm.ChatResponse;
import com.aiassistant.llm.ToolCall;
import com.aiassistant.llm.ToolDefinition;
import com.aiassistant.memory.ChatMemoryStore;
import com.aiassistant.prompt.DynamicPromptGenerator;
import com.aiassistant.rag.ContentRetriever;
import com.aiassistant.rag.Document;
import com.aiassistant.recovery.ErrorClassifier;
import com.aiassistant.recovery.ErrorType;
import com.aiassistant.recovery.FallbackService;
import com.aiassistant.recovery.RetryService;
import com.aiassistant.service.LongTermMemoryService;
import com.aiassistant.todo.TodoManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ReAct Agent 主循环。
 * <p>
 * 不再依赖 langchain4j。LLM 调用通过 {@link ChatModel} 直接走 HTTP API，
 * 工具调用采用原生 function calling 协议：
 * <ul>
 *   <li>输入：messages + tools 均为结构化 JSON</li>
 *   <li>输出：tool_calls 从响应结构化字段解析，无需正则</li>
 *   <li>工具结果以 role=tool 消息回传，完整保留调用链</li>
 * </ul>
 */
@Component
@Slf4j
public class AgentLoop {

    @Value("${workorder.memory.enabled:false}")
    private boolean longTermMemoryEnabled;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private ToolDispatcher toolDispatcher;

    @Autowired
    private DynamicPromptGenerator dynamicPromptGenerator;

    @Autowired
    private ContentRetriever contentRetriever;

    @Autowired
    private ChatMemoryStore chatMemoryStore;

    @Autowired
    private LongTermMemoryService longTermMemoryService;

    @Autowired
    private SessionContext sessionContext;

    @Autowired
    private HookRegistry hookRegistry;

    @Autowired
    private ErrorClassifier errorClassifier;

    @Autowired
    private RetryService retryService;

    @Autowired
    private FallbackService fallbackService;

    @Autowired
    private TodoManager todoManager;

    private static final int MAX_ROUNDS = 20;
    private static final int NAG_REMINDER_ROUNDS = 3;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Flux<String> run(Long sessionId, String query, String token) {
        return Flux.create(sink -> {
            try {
                sessionContext.setSession(sessionId, token);
                hookRegistry.executeHooks(HookType.SESSION_START,
                        HookContext.builder().sessionId(sessionId).build());

                String result = executeReActLoop(sessionId, query);
                sink.next(result);
                sink.complete();
            } catch (Exception e) {
                log.error("Agent执行失败: {}", e.getMessage());
                sink.error(e);
            } finally {
                hookRegistry.executeHooks(HookType.SESSION_END,
                        HookContext.builder().sessionId(sessionId).build());
                sessionContext.clear();
            }
        });
    }

    private String executeReActLoop(Long sessionId, String initialQuery) {
        List<ChatMessage> messages = new ArrayList<>();

        longTermMemoryService.initializeMemoryDirectory();

        String longTermMemoryContext = loadLongTermMemory(sessionId);
        messages.add(ChatMessage.system(buildSystemPrompt(longTermMemoryContext)));

        String ragContext = retrieveKnowledge(initialQuery);
        if (ragContext != null && !ragContext.isEmpty()) {
            messages.add(ChatMessage.system("[知识库信息]\n" + ragContext));
        }

        List<ChatMessage> history = loadSessionHistory(sessionId);
        if (!history.isEmpty()) {
            messages.addAll(history);
        }
        messages.add(ChatMessage.user(initialQuery));

        List<ToolDefinition> tools = toolDispatcher.getToolDefinitions();

        StringBuilder conversationHistory = new StringBuilder();
        conversationHistory.append("用户: ").append(initialQuery).append("\n");

        int roundsWithoutTodoUpdate = 0;
        boolean hasActiveTodos = todoManager.hasActiveTodos(sessionId);
        boolean longTask = isLongTask(initialQuery) || hasActiveTodos;

        for (int round = 0; round < MAX_ROUNDS; round++) {
            log.info("ReAct循环第 {} 轮", round + 1);

            List<ChatMessage> messagesToSend = new ArrayList<>(messages);

            ChatResponse response;
            try {
                response = chatModel.chat(messagesToSend, tools);
            } catch (Exception e) {
                log.error("LLM调用失败: {}", e.getMessage());
                hookRegistry.executeHooks(HookType.POST_LLM_RESPONSE,
                        HookContext.builder()
                                .sessionId(sessionId)
                                .errorMessage(e.getMessage())
                                .build());
                return "抱歉，服务暂时不可用，请稍后重试。";
            }

            String responseText = response.getContent();
            log.debug("LLM响应: {}", responseText);

            hookRegistry.executeHooks(HookType.POST_LLM_RESPONSE,
                    HookContext.builder()
                            .sessionId(sessionId)
                            .llmResponse(responseText)
                            .build());

            if (response.hasToolCalls()) {
                boolean containsBusinessTool = response.getToolCalls().stream()
                        .anyMatch(tc -> !isTodoTool(tc.getFunction().getName()));
                boolean containsTodoWrite = response.getToolCalls().stream()
                        .anyMatch(tc -> "todoWrite".equals(tc.getFunction().getName()));
                if (longTask && containsBusinessTool && todoManager.getTodos(sessionId).size() < 2) {
                    messages.add(ChatMessage.user("[系统门禁] 当前请求是多阶段长任务。调用业务工具前，必须先用 todoWrite 为至少两个独立、可验证的业务阶段建立任务；本轮业务工具不会执行。"));
                    if (!containsTodoWrite) {
                        log.warn("长任务未建立 todo，拒绝推进业务工具 - sessionId: {}", sessionId);
                    }
                    continue;
                }

                // 记录 assistant 的工具调用请求（格式化输出）
                messages.add(ChatMessage.assistantWithToolCalls(responseText, response.getToolCalls()));

                boolean anyTodoCall = response.getToolCalls().stream()
                        .anyMatch(tc -> isTodoTool(tc.getFunction().getName()));
                if (anyTodoCall) {
                    hasActiveTodos = true;
                    roundsWithoutTodoUpdate = 0;
                } else if (hasActiveTodos) {
                    roundsWithoutTodoUpdate++;
                }

                // 逐个执行工具调用，结果以 role=tool 消息回传（格式化输入）
                for (ToolCall toolCall : response.getToolCalls()) {
                    String toolName = toolCall.getFunction().getName();
                    String arguments = toolCall.getFunction().getArguments();
                    log.info("执行工具调用: {} args={}", toolName, arguments);

                    if (isUnconfirmedDryRunExecution(messages, toolName, arguments, initialQuery)) {
                        String rejected = wrapToolResult(toolName, false,
                                "写操作未获授权：真实命令与最近一次 dry-run 相同，但当前用户消息不是有效的 confirm_execute JSON。必须停止并等待确认。");
                        messages.add(ChatMessage.tool(toolCall.getId(), toolName, rejected));
                        conversationHistory.append("工具结果[").append(toolName).append("]: ").append(rejected).append("\n");
                        log.warn("拒绝未确认的真实写命令 - sessionId: {}", sessionId);
                        continue;
                    }

                    hookRegistry.executeHooks(HookType.PRE_TOOL_USE,
                            HookContext.builder()
                                    .sessionId(sessionId)
                                    .toolName(toolName)
                                    .build());

                    String toolResult = executeToolWithRecovery(toolName, arguments);

                    hookRegistry.executeHooks(HookType.POST_TOOL_USE,
                            HookContext.builder()
                                    .sessionId(sessionId)
                                    .toolName(toolName)
                                    .toolResult(toolResult)
                                    .build());

                    log.info("工具执行结果: {}", toolResult.length() > 200 ? toolResult.substring(0, 200) + "..." : toolResult);

                    messages.add(ChatMessage.tool(toolCall.getId(), toolName, toolResult));
                    conversationHistory.append("工具结果[").append(toolName).append("]: ")
                            .append(toolResult).append("\n");
                }

                // Nag reminder：连续多轮未更新 todo 时提醒
                if (hasActiveTodos && roundsWithoutTodoUpdate >= NAG_REMINDER_ROUNDS) {
                    messages.add(ChatMessage.user("[系统提醒] 您有未完成的任务，请更新任务状态或继续执行。\n"
                            + todoManager.getTodoSummary(sessionId)));
                    roundsWithoutTodoUpdate = 0;
                }
            } else {
                // 无工具调用 → 最终回答（按结构化规范解析）
                String finalAnswer = parseStructuredAnswer(responseText);
                messages.add(ChatMessage.assistant(finalAnswer));
                conversationHistory.append("助手: ").append(finalAnswer).append("\n");
                chatMemoryStore.updateMessages(sessionId, messages);
                extractAndSaveLongTermMemory(sessionId, conversationHistory.toString());
                return finalAnswer;
            }
        }

        log.warn("达到最大思考次数限制");
        chatMemoryStore.updateMessages(sessionId, messages);
        extractAndSaveLongTermMemory(sessionId, conversationHistory.toString());
        return "抱歉，我已经尝试了多次，但未能完成您的请求。请简化问题或分步骤提问。";
    }

    private boolean isTodoTool(String toolName) {
        return toolName != null && (toolName.startsWith("todo")
                || toolName.equals("todoWrite")
                || toolName.equals("todoList")
                || toolName.equals("todoUpdate")
                || toolName.equals("todoClear"));
    }

    /**
     * 通用长任务识别：同时出现多个动作和明确的依赖/顺序关系。
     * 不包含任何工单领域词，避免把业务规则固化到 Agent 主循环。
     */
    private boolean isLongTask(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        String[] actionMarkers = {"创建", "查询", "生成", "修改", "删除", "分析", "验证", "说明", "告诉", "执行", "审批", "分配",
                "create", "query", "search", "generate", "update", "delete", "analyze", "verify", "explain", "execute"};
        int actionCount = 0;
        for (String marker : actionMarkers) {
            if (normalized.contains(marker)) {
                actionCount++;
            }
        }
        String[] dependencyMarkers = {"然后", "之后", "完成后", "成功后", "再", "最后", "接着", "并且", "并告诉", "先", "后",
                "then", "after", "before", "finally", "next"};
        boolean hasDependency = false;
        for (String marker : dependencyMarkers) {
            if (normalized.contains(marker)) {
                hasDependency = true;
                break;
            }
        }
        return actionCount >= 3 && hasDependency;
    }

    private boolean isUnconfirmedDryRunExecution(List<ChatMessage> messages, String toolName,
                                                  String arguments, String currentUserMessage) {
        if (!"executeCliCommand".equals(toolName)) {
            return false;
        }
        String currentCommand = extractCliCommand(arguments);
        if (currentCommand == null || currentCommand.contains("--dry-run")) {
            return false;
        }
        String latestDryRun = findLatestDryRunCommand(messages);
        if (latestDryRun == null || !removeDryRun(latestDryRun).equals(normalizeCommand(currentCommand))) {
            return false;
        }
        return !isValidConfirmation(currentUserMessage);
    }

    private String findLatestDryRunCommand(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message.getToolCalls() == null) {
                continue;
            }
            for (int j = message.getToolCalls().size() - 1; j >= 0; j--) {
                ToolCall call = message.getToolCalls().get(j);
                if ("executeCliCommand".equals(call.getFunction().getName())) {
                    String command = extractCliCommand(call.getFunction().getArguments());
                    if (command != null && command.contains("--dry-run")) {
                        return command;
                    }
                }
            }
        }
        return null;
    }

    private String extractCliCommand(String arguments) {
        try {
            JsonNode node = MAPPER.readTree(arguments);
            String[] keys = {"CLI命令字符串", "command", "content"};
            for (String key : keys) {
                if (node.hasNonNull(key)) {
                    return normalizeCommand(node.get(key).asText());
                }
            }
        } catch (Exception ignored) {
            // 无法解析时交给原工具参数校验。
        }
        return null;
    }

    private String removeDryRun(String command) {
        return normalizeCommand(command.replaceFirst("(?i)\\s+--dry-run(?=\\s)", ""));
    }

    private String normalizeCommand(String command) {
        return command == null ? null : command.trim().replaceAll("\\s+", " ");
    }

    private boolean isValidConfirmation(String message) {
        try {
            JsonNode node = MAPPER.readTree(message);
            return "confirm_execute".equals(node.path("action").asText())
                    && "last_dry_run".equals(node.path("target").asText())
                    && node.path("confirmed").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    private List<ChatMessage> loadSessionHistory(Long sessionId) {
        try {
            List<ChatMessage> history = chatMemoryStore.getMessages(sessionId);
            if (history == null || history.isEmpty()) {
                return List.of();
            }

            List<ChatMessage> filtered = new ArrayList<>();
            for (ChatMessage message : history) {
                if (message == null || "system".equals(message.getRole())) {
                    continue;
                }
                filtered.add(message);
            }
            return filtered;
        } catch (Exception e) {
            log.warn("加载会话历史失败: sessionId={}, error={}", sessionId, e.getMessage());
            return List.of();
        }
    }

    private String executeToolWithRecovery(String toolName, String arguments) {
        try {
            String raw = retryService.executeWithRetry(() -> toolDispatcher.executeTool(toolName, arguments), toolName);
            // 工具方法未抛异常不代表业务成功：CLI 等工具返回的 JSON 中 code!=0 表示业务错误
            // （如 401 认证失败），需标记 success=false 让 LLM 感知失败并按 SKILL.md 处理
            boolean success = !isErrorResponse(raw);
            return wrapToolResult(toolName, success, raw);
        } catch (Exception e) {
            log.error("工具执行失败: {}", e.getMessage());
            ErrorType errorType = errorClassifier.classify(e);
            String errorMsg = errorType == ErrorType.USER_ACTIONABLE
                    ? fallbackService.getErrorMessageForUser(errorType, e.getMessage())
                    : "工具执行失败: " + e.getMessage();
            return wrapToolResult(toolName, false, errorMsg);
        }
    }

    /**
     * 检测工具返回的 JSON 结果是否为错误响应。
     * <p>
     * CLI 工具约定：顶层 {@code code} 字段为 0 表示成功，非 0 表示失败
     * （如 401 认证错误、2 认证错误、4 参数错误等）。
     * 非 JSON 结果不视为错误（保持对纯文本工具的兼容）。
     * <p>
     * 此方法为通用的结果状态检测，不含任何登录/业务逻辑，
     * 保证 skill 和 tool 可插拔。
     */
    private boolean isErrorResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{")) {
            return false;
        }
        try {
            JsonNode node = MAPPER.readTree(trimmed);
            JsonNode codeNode = node.path("code");
            if (codeNode.isInt() && codeNode.asInt() != 0) {
                return true;
            }
        } catch (Exception e) {
            // 非 JSON 或解析失败，不视为错误
        }
        return false;
    }

    /**
     * 将工具执行结果包装为结构化 JSON 字符串回传给 LLM（role=tool 消息的 content）。
     * 结构：{"tool":"工具名","success":true/false,"result":"执行结果"}
     * 解析失败时容错回退为原始结果字符串。
     */
    private String wrapToolResult(String toolName, boolean success, String result) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("tool", toolName);
        node.put("success", success);
        node.put("result", result == null ? "" : result);
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            log.warn("包装工具结果为结构化 JSON 失败，回退原始结果: {}", e.getMessage());
            return result == null ? "" : result;
        }
    }

    /**
     * 解析 LLM 最终回答，剥离 &lt;thinking&gt;...&lt;/thinking&gt; 标签内容。
     * <p>
     * 提示词采用 thinking 标签方案：LLM 在标签内推理，答案在标签外。
     * 系统解析时剥离 thinking 部分，仅暴露标签外内容给用户。
     * 若剥离后为空（LLM 只输出了 thinking），回退到原始 content。
     */
    private String parseStructuredAnswer(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        // 剥离所有 <thinking>...</thinking> 块（DOTALL 模式匹配跨行内容）
        String stripped = content.replaceAll("(?s)<thinking>.*?</thinking>", "");
        String trimmed = stripped.trim();
        // 剥离后为空则回退原文，避免丢失信息
        return trimmed.isEmpty() ? content : trimmed;
    }

    private String buildSystemPrompt(String longTermMemoryContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(dynamicPromptGenerator.generate());
        if (longTermMemoryContext != null && !longTermMemoryContext.isEmpty()) {
            prompt.append("\n## 用户记忆\n");
            prompt.append(longTermMemoryContext);
        }
        return prompt.toString();
    }

    private String loadLongTermMemory(Long sessionId) {
        if (!longTermMemoryEnabled) {
            log.debug("长期记忆已禁用，跳过加载");
            return null;
        }
        try {
            List<LongTermMemoryService.MemoryEntry> memories = longTermMemoryService.loadMemories(sessionId);
            if (memories == null || memories.isEmpty()) {
                return null;
            }
            return longTermMemoryService.getMemorySummary(sessionId);
        } catch (Exception e) {
            log.warn("加载长期记忆失败: {}", e.getMessage());
            return null;
        }
    }

    private void extractAndSaveLongTermMemory(Long sessionId, String conversation) {
        if (!longTermMemoryEnabled) {
            log.debug("长期记忆已禁用，跳过保存");
            return;
        }
        try {
            longTermMemoryService.extractMemories(sessionId, conversation);
        } catch (Exception e) {
            log.warn("提取和保存长期记忆失败: {}", e.getMessage());
        }
    }

    private String retrieveKnowledge(String query) {
        try {
            List<Document> documents = contentRetriever.retrieve(query);
            if (documents == null || documents.isEmpty()) {
                return null;
            }
            StringBuilder context = new StringBuilder();
            for (Document doc : documents) {
                context.append(doc.getText()).append("\n\n");
            }
            return context.toString().trim();
        } catch (Exception e) {
            log.warn("RAG检索失败: {}", e.getMessage());
            return null;
        }
    }
}
