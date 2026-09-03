package com.aiassistant.agent;

import com.aiassistant.llm.ToolDefinition;
import com.aiassistant.tool.annotation.P;
import com.aiassistant.tool.annotation.Tool;
import com.aiassistant.tools.CliExecutorTools;
import com.aiassistant.todo.TodoWriteTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具分发器：扫描自定义 {@link Tool} 注解，构建结构化 {@link ToolDefinition}（含 JSON Schema）
 * 供 LLM function calling 使用，并通过反射执行工具调用。
 * <p>
 * 不再依赖 dev.langchain4j.agent.tool.Tool / P。
 */
@Component
@Slf4j
public class ToolDispatcher {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 系统自动注入的参数，不暴露给 LLM */
    private static final String AUTO_INJECTED_PARAM = "sessionId";

    private final Map<String, ToolHandler> toolHandlers = new HashMap<>();

    @Autowired
    private CliExecutorTools cliExecutorTools;

    @Autowired
    private TodoWriteTools todoWriteTools;

    @PostConstruct
    public void initialize() {
        registerTools(cliExecutorTools);
        registerTools(todoWriteTools);
        log.info("已注册 {} 个工具", toolHandlers.size());
    }

    private void registerTools(Object toolInstance) {
        for (Method method : toolInstance.getClass().getDeclaredMethods()) {
            Tool toolAnnotation = method.getAnnotation(Tool.class);
            if (toolAnnotation == null) {
                continue;
            }
            String toolName = method.getName();
            String description = toolAnnotation.value().length > 0 ? toolAnnotation.value()[0] : toolName;
            String[] paramNames = extractParamNames(method);

            toolHandlers.put(toolName, new ToolHandler(toolInstance, method, paramNames, description));
            log.debug("注册工具: {} (参数: {})", toolName,
                    paramNames != null ? String.join(",", paramNames) : "none");
        }
    }

    private String[] extractParamNames(Method method) {
        Parameter[] params = method.getParameters();
        String[] paramNames = new String[params.length];
        for (int i = 0; i < params.length; i++) {
            P pAnnotation = params[i].getAnnotation(P.class);
            if (pAnnotation != null && !pAnnotation.value().isEmpty()) {
                paramNames[i] = pAnnotation.value();
            } else {
                paramNames[i] = params[i].getName();
            }
        }
        return paramNames;
    }

    /**
     * 生成全部工具的 ToolDefinition 列表，用于 LLM function calling 输入。
     */
    public List<ToolDefinition> getToolDefinitions() {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (ToolHandler handler : toolHandlers.values()) {
            definitions.add(buildDefinition(handler));
        }
        return definitions;
    }

    private ToolDefinition buildDefinition(ToolHandler handler) {
        ObjectNode parameters = MAPPER.createObjectNode();
        parameters.put("type", "object");
        ObjectNode properties = parameters.putObject("properties");
        ArrayNode required = parameters.putArray("required");

        Parameter[] methodParams = handler.method().getParameters();
        String[] paramNames = handler.paramNames();
        for (int i = 0; i < methodParams.length; i++) {
            Parameter param = methodParams[i];
            String paramName = paramNames != null && i < paramNames.length ? paramNames[i] : param.getName();

            // 系统自动注入参数不暴露给 LLM
            if (AUTO_INJECTED_PARAM.equals(paramName)) {
                continue;
            }

            properties.set(paramName, buildPropertySchema(param.getType()));
            required.add(paramName);
        }

        return ToolDefinition.of(handler.method().getName(), handler.description(), parameters);
    }

    private JsonNode buildPropertySchema(Class<?> type) {
        ObjectNode node = MAPPER.createObjectNode();
        if (type == String.class) {
            node.put("type", "string");
        } else if (type == Integer.class || type == int.class
                || type == Long.class || type == long.class) {
            node.put("type", "integer");
        } else if (type == Boolean.class || type == boolean.class) {
            node.put("type", "boolean");
        } else if (type == Double.class || type == double.class
                || type == Float.class || type == float.class) {
            node.put("type", "number");
        } else if (List.class.isAssignableFrom(type)) {
            node.put("type", "array");
            ObjectNode items = MAPPER.createObjectNode();
            items.put("type", "string");
            node.set("items", items);
        } else if (Map.class.isAssignableFrom(type)) {
            node.put("type", "object");
        } else {
            node.put("type", "string");
        }
        return node;
    }

    /**
     * 执行工具调用。
     *
     * @param toolName  工具名称（方法名）
     * @param arguments JSON 字符串参数（来自 LLM tool_calls.function.arguments）
     */
    public String executeTool(String toolName, String arguments) {
        Map<String, Object> parameters = parseArguments(arguments);
        return executeTool(toolName, parameters);
    }

    public String executeTool(String toolName, Map<String, Object> parameters) {
        ToolHandler handler = toolHandlers.get(toolName);
        if (handler == null) {
            return "错误：未知工具 '" + toolName + "'";
        }
        try {
            Object[] args = buildArguments(handler, parameters);
            Object result = handler.method().invoke(handler.instance(), args);
            return result != null ? result.toString() : "工具执行成功，无返回结果";
        } catch (Exception e) {
            log.error("工具执行失败: {} - {}", toolName, e.getMessage());
            return "工具执行失败: " + unwrapMessage(e);
        }
    }

    private String unwrapMessage(Exception e) {
        Throwable cause = e.getCause();
        return cause != null ? cause.getMessage() : e.getMessage();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return new HashMap<>();
        }
        try {
            JsonNode node = MAPPER.readTree(arguments);
            Map<String, Object> map = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> map.put(entry.getKey(), convertNode(entry.getValue())));
            return map;
        } catch (Exception e) {
            log.warn("解析工具参数 JSON 失败，回退空参数: {} - {}", arguments, e.getMessage());
            return new HashMap<>();
        }
    }

    private Object convertNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong()) {
            return node.asLong();
        }
        if (node.isDouble() || node.isFloat()) {
            return node.asDouble();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode item : node) {
                list.add(convertNode(item));
            }
            return list;
        }
        return node.asText();
    }

    private Object[] buildArguments(ToolHandler handler, Map<String, Object> parameters) {
        Parameter[] methodParams = handler.method().getParameters();
        String[] paramNames = handler.paramNames();
        Object[] args = new Object[methodParams.length];

        for (int i = 0; i < methodParams.length; i++) {
            Parameter param = methodParams[i];
            String paramName = paramNames != null && i < paramNames.length ? paramNames[i] : param.getName();

            Object value = parameters.get(paramName);
            if (value == null) {
                value = parameters.get(paramName.toLowerCase());
            }

            // sessionId 由系统自动注入
            if (value == null && AUTO_INJECTED_PARAM.equals(paramName)) {
                value = com.aiassistant.common.SessionContext.getStaticMemoryId();
            }

            // 兼容常见别名
            if (value == null) {
                value = resolveAlias(paramName, parameters);
            }

            args[i] = (value != null) ? convertValue(value, param.getType()) : null;
        }
        return args;
    }

    private Object resolveAlias(String paramName, Map<String, Object> parameters) {
        switch (paramName) {
            case "filepath":
            case "文件路径":
                return firstNonNull(parameters.get("filename"), parameters.get("filepath"), parameters.get("path"), parameters.get("command"));
            case "dataCode":
            case "skillName":
            case "技能名称":
            case "CLI命令字符串":
                return parameters.get("command");
            case "内容":
                return firstNonNull(parameters.get("content"), parameters.get("command"));
            case "表头":
                return parameters.get("headers");
            case "数据行":
                return parameters.get("rows");
            default:
                return null;
        }
    }

    private Object firstNonNull(Object... values) {
        for (Object v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }
        if (targetType == String.class) {
            return value.toString();
        }
        if (targetType == Integer.class || targetType == int.class) {
            return Integer.parseInt(value.toString());
        }
        if (targetType == Long.class || targetType == long.class) {
            return Long.parseLong(value.toString());
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.parseBoolean(value.toString());
        }
        if (targetType == Double.class || targetType == double.class) {
            return Double.parseDouble(value.toString());
        }
        if (List.class.isAssignableFrom(targetType)) {
            if (value instanceof List) {
                return value;
            }
            List<Object> list = new ArrayList<>();
            list.add(value);
            return list;
        }
        return value;
    }

    public Map<String, String> listTools() {
        Map<String, String> tools = new LinkedHashMap<>();
        for (ToolHandler handler : toolHandlers.values()) {
            tools.put(handler.method().getName(), handler.description());
        }
        return tools;
    }

    public boolean isToolRegistered(String toolName) {
        return toolHandlers.containsKey(toolName);
    }

    public record ToolHandler(Object instance, Method method, String[] paramNames, String description) {}
}
