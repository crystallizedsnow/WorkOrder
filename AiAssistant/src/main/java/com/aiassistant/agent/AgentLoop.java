package com.aiassistant.agent;

import com.aiassistant.common.SessionContext;
import com.aiassistant.hook.HookContext;
import com.aiassistant.hook.HookRegistry;
import com.aiassistant.hook.HookType;
import com.aiassistant.prompt.DynamicPromptGenerator;
import com.aiassistant.recovery.ErrorClassifier;
import com.aiassistant.recovery.ErrorType;
import com.aiassistant.recovery.FallbackService;
import com.aiassistant.recovery.RetryService;
import com.aiassistant.service.LongTermMemoryService;
import com.aiassistant.todo.TodoManager;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class AgentLoop {

    @Autowired
    @Qualifier("zhipuChatModel")
    private ChatModel chatModel;

    @Autowired
    private ToolDispatcher toolDispatcher;

    @Autowired
    private DynamicPromptGenerator dynamicPromptGenerator;

    @Autowired
    private ContentRetriever contentRetriever;

    @Autowired
    private ChatMemoryProvider chatMemoryProvider;

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
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
            "调用\\s*(\\w+)(?:\\(([^)]*)\\))?", Pattern.MULTILINE
    );
    
    private static final Pattern ACTION_TOOL_PATTERN = Pattern.compile(
            "行动\\s*[：:]\\s*(调用\\s*)?(\\w+)(?:\\(([^)]*)\\))?", Pattern.MULTILINE
    );
    
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
            "```(?:\\w+\\s*)?\\s*(\\w+)\\(([^)]*)\\)\\s*```", Pattern.MULTILINE
    );
    
    private static final Pattern DIRECT_CALL_PATTERN = Pattern.compile(
            "\\b(\\w+)\\(([^)]+)\\)", Pattern.MULTILINE
    );

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
        SystemMessage systemMessage = SystemMessage.from(buildSystemPrompt(longTermMemoryContext));
        messages.add(systemMessage);

        messages.add(UserMessage.from(initialQuery));

        StringBuilder conversationHistory = new StringBuilder();
        conversationHistory.append("用户: ").append(initialQuery).append("\n");

        int roundsWithoutTodoUpdate = 0;
        boolean hasActiveTodos = false;

        for (int round = 0; round < MAX_ROUNDS; round++) {
            log.info("ReAct循环第 {} 轮", round + 1);

            List<ChatMessage> messagesToSend = new ArrayList<>(messages);

            String ragContext = retrieveKnowledge(initialQuery);
            if (ragContext != null && !ragContext.isEmpty()) {
                messagesToSend.add(UserMessage.from("[知识库信息]\n" + ragContext));
            }

            ChatResponse response;
            try {
                response = chatModel.chat(messagesToSend);
            } catch (Exception e) {
                log.error("LLM调用失败: {}", e.getMessage());
                hookRegistry.executeHooks(HookType.POST_LLM_RESPONSE,
                        HookContext.builder()
                                .sessionId(sessionId)
                                .errorMessage(e.getMessage())
                                .build());
                return "抱歉，服务暂时不可用，请稍后重试。";
            }

            String responseText = response.aiMessage().text();
            log.debug("LLM响应: {}", responseText);

            hookRegistry.executeHooks(HookType.POST_LLM_RESPONSE,
                    HookContext.builder()
                            .sessionId(sessionId)
                            .llmResponse(responseText)
                            .build());

            messages.add(response.aiMessage());

            String toolName = null;
            String paramsStr = null;
            
            Matcher toolCallMatcher = TOOL_CALL_PATTERN.matcher(responseText);
            if (toolCallMatcher.find()) {
                toolName = toolCallMatcher.group(1);
                paramsStr = toolCallMatcher.group(2);
            } else {
                Matcher actionMatcher = ACTION_TOOL_PATTERN.matcher(responseText);
                if (actionMatcher.find()) {
                    toolName = actionMatcher.group(2);
                    paramsStr = actionMatcher.group(3);
                } else {
                    Matcher codeBlockMatcher = CODE_BLOCK_PATTERN.matcher(responseText);
                    if (codeBlockMatcher.find()) {
                        toolName = codeBlockMatcher.group(1);
                        paramsStr = codeBlockMatcher.group(2);
                    } else {
                        Matcher directCallMatcher = DIRECT_CALL_PATTERN.matcher(responseText);
                        if (directCallMatcher.find()) {
                            String fullMatch = directCallMatcher.group(0);
                            if (isValidToolCall(fullMatch)) {
                                toolName = directCallMatcher.group(1);
                                paramsStr = directCallMatcher.group(2);
                            }
                        }
                    }
                }
            }
            
            if (toolName != null) {
                log.info("检测到工具调用: {}({})", toolName, paramsStr);

                Map<String, Object> parameters = parseParameters(paramsStr);
                
                if (isTodoTool(toolName)) {
                    parameters.put("sessionId", sessionId);
                    hasActiveTodos = true;
                    roundsWithoutTodoUpdate = 0;
                } else if (hasActiveTodos) {
                    roundsWithoutTodoUpdate++;
                    if (roundsWithoutTodoUpdate >= NAG_REMINDER_ROUNDS) {
                        messages.add(UserMessage.from("[系统提醒] 您有未完成的任务，请更新任务状态或继续执行。\n" + todoManager.getTodoSummary(sessionId)));
                        roundsWithoutTodoUpdate = 0;
                        continue;
                    }
                }

                hookRegistry.executeHooks(HookType.PRE_TOOL_USE,
                        HookContext.builder()
                                .sessionId(sessionId)
                                .toolName(toolName)
                                .build());

                String toolResult = executeToolWithRecovery(sessionId, toolName, parameters);

                hookRegistry.executeHooks(HookType.POST_TOOL_USE,
                        HookContext.builder()
                                .sessionId(sessionId)
                                .toolName(toolName)
                                .toolResult(toolResult)
                                .build());

                log.info("工具执行结果: {}", toolResult.length() > 200 ? toolResult.substring(0, 200) + "..." : toolResult);

                messages.add(UserMessage.from("工具执行结果:\n" + toolResult));
                conversationHistory.append("助手: ").append(responseText).append("\n");
                conversationHistory.append("工具结果: ").append(toolResult).append("\n");
            } else {
                conversationHistory.append("助手: ").append(responseText).append("\n");
                chatMemoryStore.updateMessages(sessionId, messages);
                extractAndSaveLongTermMemory(sessionId, conversationHistory.toString());
                return responseText;
            }
        }

        log.warn("达到最大思考次数限制");
        chatMemoryStore.updateMessages(sessionId, messages);
        extractAndSaveLongTermMemory(sessionId, conversationHistory.toString());
        return "抱歉，我已经尝试了多次，但未能完成您的请求。请简化问题或分步骤提问。";
    }

    private boolean isTodoTool(String toolName) {
        return toolName.startsWith("todo") || 
               toolName.equals("todoWrite") || 
               toolName.equals("todoList") || 
               toolName.equals("todoUpdate") || 
               toolName.equals("todoClear");
    }

    private String executeToolWithRecovery(Long sessionId, String toolName, Map<String, Object> parameters) {
        try {
            return retryService.executeWithRetry(() -> {
                return toolDispatcher.executeTool(toolName, parameters);
            }, toolName);
        } catch (Exception e) {
            log.error("工具执行失败: {}", e.getMessage());
            
            ErrorType errorType = errorClassifier.classify(e);
            
            if (errorType == ErrorType.USER_ACTIONABLE) {
                return fallbackService.getErrorMessageForUser(errorType, e.getMessage());
            }
            
            return "工具执行失败: " + e.getMessage();
        }
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
        try {
            longTermMemoryService.extractMemories(sessionId, conversation);
        } catch (Exception e) {
            log.warn("提取和保存长期记忆失败: {}", e.getMessage());
        }
    }

    private String retrieveKnowledge(String query) {
        try {
            var contents = contentRetriever.retrieve(Query.from(query));
            if (contents == null || contents.isEmpty()) {
                return null;
            }

            StringBuilder context = new StringBuilder();
            for (var content : contents) {
                context.append(content.toString()).append("\n\n");
            }
            return context.toString().trim();
        } catch (Exception e) {
            log.warn("RAG检索失败: {}", e.getMessage());
            return null;
        }
    }

    private boolean isValidToolCall(String fullMatch) {
        String lowerMatch = fullMatch.toLowerCase();
        return lowerMatch.contains("getskillcontent") ||
               lowerMatch.contains("loadskill") ||
               lowerMatch.contains("createtxtfile") ||
               lowerMatch.contains("readtxtfile") ||
               lowerMatch.contains("createexcelfile") ||
               lowerMatch.contains("readexcelfile") ||
               lowerMatch.contains("createmarkdownfile") ||
               lowerMatch.contains("readmarkdownfile") ||
               lowerMatch.contains("todowrite") ||
               lowerMatch.contains("todolist") ||
               lowerMatch.contains("todoupdate") ||
               lowerMatch.contains("todoclear");
    }

    private Map<String, Object> parseParameters(String paramsStr) {
        Map<String, Object> parameters = new HashMap<>();
        if (paramsStr == null || paramsStr.isEmpty()) {
            return parameters;
        }

        paramsStr = paramsStr.trim();

        if (paramsStr.startsWith("'") && paramsStr.endsWith("'")) {
            String value = paramsStr.substring(1, paramsStr.length() - 1);
            parameters.put("command", value);
            return parameters;
        }

        if (paramsStr.startsWith("\"") && paramsStr.endsWith("\"")) {
            String value = paramsStr.substring(1, paramsStr.length() - 1);
            parameters.put("command", value);
            return parameters;
        }

        String[] pairs = paramsStr.split(",");

        for (String pair : pairs) {
            pair = pair.trim();
            if (pair.isEmpty()) continue;

            int eqIndex = pair.indexOf("=");
            if (eqIndex >= 0) {
                String key = pair.substring(0, eqIndex).trim();
                String value = pair.substring(eqIndex + 1).trim();

                value = value.replace("\"", "").replace("'", "");
                parameters.put(key, value);
            } else {
                String value = pair.replace("\"", "").replace("'", "").trim();
                if (!value.isEmpty()) {
                    parameters.put("command", value);
                }
            }
        }

        return parameters;
    }
}