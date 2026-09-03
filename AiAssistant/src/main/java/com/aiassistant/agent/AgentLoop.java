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
import com.aiassistant.memory.MemoryContext;
import com.aiassistant.memory.SessionMemoryService;
import com.aiassistant.memory.ToolResultProtector;
import com.aiassistant.prompt.DynamicPromptGenerator;
import com.aiassistant.rag.ContentRetriever;
import com.aiassistant.rag.Document;
import com.aiassistant.rag.RagContext;
import com.aiassistant.rag.CitationService;
import com.aiassistant.rag.QueryContextualizer;
import com.aiassistant.rag.RagStateService;
import com.aiassistant.rag.RagStatus;
import com.aiassistant.recovery.ErrorClassifier;
import com.aiassistant.recovery.ErrorType;
import com.aiassistant.recovery.FallbackService;
import com.aiassistant.recovery.RetryService;
import com.aiassistant.todo.TodoManager;
import com.aiassistant.channel.model.AgentRequest;
import com.aiassistant.channel.model.ChannelType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

    @Value("${workorder.agent.max-rounds:10}")
    private int maxRounds;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private ToolDispatcher toolDispatcher;

    @Autowired
    private DynamicPromptGenerator dynamicPromptGenerator;

    @Autowired
    private ContentRetriever contentRetriever;

    @Autowired
    private CitationService citationService;

    @Autowired
    private QueryContextualizer queryContextualizer;

    @Autowired
    private RagStateService ragStateService;

    @Autowired
    private SessionMemoryService sessionMemoryService;

    @Autowired
    private ToolResultProtector toolResultProtector;

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

    private static final int NAG_REMINDER_ROUNDS = 3;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Flux<String> run(Long sessionId, String query, String token) {
        return run(new AgentRequest(sessionId, null, query, token, ChannelType.WEB, null, null, null, null));
    }

    public Flux<String> run(AgentRequest request) {
        return Flux.create(sink -> {
            Long sessionId = request.sessionId(); String query = request.query(); String token = request.accessToken();
            try {
                sessionContext.setSession(sessionId, token);
                sessionContext.setRequest(request);
                hookRegistry.executeHooks(HookType.SESSION_START,
                        HookContext.builder().sessionId(sessionId).build());

                String result = executeReActLoop(request);
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

    private String executeReActLoop(AgentRequest request) {
        Long sessionId = request.sessionId();
        String initialQuery = request.query();
        String retrievalQuery = queryContextualizer.contextualize(initialQuery,
                sessionMemoryService.recentUserMessages(request, 2));
        RagContext ragContext = retrieveKnowledge(retrievalQuery);
        List<ToolDefinition> tools = toolDispatcher.getToolDefinitions();
        MemoryContext memoryContext = sessionMemoryService.prepare(request, dynamicPromptGenerator.generate(),
                ragContext.promptContext(), initialQuery, tools);
        List<ChatMessage> messages = new ArrayList<>(memoryContext.modelMessages());

        int roundsWithoutTodoUpdate = 0;
        boolean hasActiveTodos = todoManager.hasActiveTodos(sessionId);
        boolean longTask = isLongTask(initialQuery) || hasActiveTodos;
        Set<String> executedToolCalls = new HashSet<>();

        for (int round = 0; round < maxRounds; round++) {
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

            if ((responseText == null || responseText.isBlank()) && !response.hasToolCalls()) {
                log.warn("LLM-EMPTY-RESPONSE sessionId={} round={} finishReason={} promptTokens={} completionTokens={}",
                        sessionId, round + 1, response.getFinishReason(), response.getPromptTokens(),
                        response.getCompletionTokens());
                return "模型本轮未生成有效内容，请稍后重试。";
            }

            hookRegistry.executeHooks(HookType.POST_LLM_RESPONSE,
                    HookContext.builder()
                            .sessionId(sessionId)
                            .llmResponse(responseText)
                            .build());

            if (response.hasToolCalls()) {
                if (response.getToolCalls().stream().anyMatch(this::isForbiddenCredentialToolCall)) {
                    log.warn("模型生成了禁止的凭证操作，已在 AgentLoop 层丢弃 - sessionId: {}", sessionId);
                    messages.add(ChatMessage.user("[系统安全约束] 当前请求身份已经建立。不得执行任何登录、刷新凭证、读取凭证文件或在命令中传递凭证的操作。请从 DISCOVER 开始，直接使用业务 CLI 命令。"));
                    continue;
                }
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

                    String callFingerprint = toolName + "\n" + (arguments == null ? "" : arguments.trim());
                    if (!executedToolCalls.add(callFingerprint)) {
                        String duplicate = wrapToolResult(toolName, false,
                                "同一轮中相同工具和参数已经执行，重复调用不会得到更多数据。请使用已有结果回答，或修改查询条件。");
                        messages.add(ChatMessage.tool(toolCall.getId(), toolName, duplicate));
                        log.warn("TOOL-DUPLICATE-BLOCKED sessionId={} toolName={} args={}", sessionId, toolName, arguments);
                        continue;
                    }

                    hookRegistry.executeHooks(HookType.PRE_TOOL_USE,
                            HookContext.builder()
                                    .sessionId(sessionId)
                                    .toolName(toolName)
                                    .build());

                    String toolResult = toolResultProtector.protect(executeToolWithRecovery(toolName, arguments));

                    hookRegistry.executeHooks(HookType.POST_TOOL_USE,
                            HookContext.builder()
                                    .sessionId(sessionId)
                                    .toolName(toolName)
                                    .toolResult(toolResult)
                                    .build());

                    log.info("工具执行结果: {}", toolResult.length() > 200 ? toolResult.substring(0, 200) + "..." : toolResult);
                    messages.add(ChatMessage.tool(toolCall.getId(), toolName, toolResult));

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
                CitationService.CitationValidation citationValidation = citationService.validateAndRender(finalAnswer, ragContext);
                finalAnswer = citationValidation.answer();
                messages.add(ChatMessage.assistant(finalAnswer));
                sessionMemoryService.save(request, memoryContext, messages, "ACTIVE");
                return finalAnswer;
            }
        }

        log.warn("达到最大思考次数限制");
        sessionMemoryService.save(request, memoryContext, messages, "INTERRUPTED");
        return "抱歉，我已经尝试了多次，但未能完成您的请求。请简化问题或分步骤提问。";
    }

    private boolean isTodoTool(String toolName) {
        return toolName != null && (toolName.startsWith("todo")
                || toolName.equals("todoWrite")
                || toolName.equals("todoList")
                || toolName.equals("todoUpdate")
                || toolName.equals("todoClear"));
    }

    private boolean isForbiddenCredentialToolCall(ToolCall call) {
        if (call == null || call.getFunction() == null) {
            return false;
        }
        String name = call.getFunction().getName();
        String arguments = call.getFunction().getArguments();
        if (name != null && name.toLowerCase(Locale.ROOT).contains("login")) {
            return true;
        }
        if (!"executeCliCommand".equals(name) || arguments == null) {
            return false;
        }
        String command = extractCliCommand(arguments);
        if (command == null) {
            return false;
        }
        String normalized = " " + command.toLowerCase(Locale.ROOT) + " ";
        return normalized.matches("(?s).*\\s(?:auth\\s+)?(?:login|refresh)\\s.*")
                || normalized.matches("(?s).*\\s(?:--password|--token|-t)\\s+.*");
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

    private String normalizeCommand(String command) {
        return command == null ? null : command.trim().replaceAll("\\s+", " ");
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

    private RagContext retrieveKnowledge(String query) {
        try {
            List<Document> documents = contentRetriever.retrieve(query);
            if (documents == null || documents.isEmpty()) {
                RagStatus status = ragStateService.snapshot().status();
                if (status == RagStatus.FAILED || status == RagStatus.INITIALIZING || status == RagStatus.BUILDING) {
                    return RagContext.notice("知识库当前尚未就绪。不要猜测项目特有的状态、权限、SLA 或流程规则；如问题依赖这些知识，请明确告知用户知识库暂不可用。");
                }
                if (status == RagStatus.READY || status == RagStatus.DEGRADED) {
                    return RagContext.notice("本次未从可信知识库检索到依据。不要猜测项目特有的状态、权限、SLA 或流程规则。");
                }
                return RagContext.empty();
            }
            return citationService.prepare(documents);
        } catch (Exception e) {
            log.warn("RAG检索失败: {}", e.getMessage());
            return RagContext.empty();
        }
    }
}
