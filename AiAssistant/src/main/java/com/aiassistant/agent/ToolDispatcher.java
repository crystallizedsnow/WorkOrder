package com.aiassistant.agent;

import com.aiassistant.tools.CliExecutorTools;
import com.aiassistant.tools.FileTools;
import com.aiassistant.tools.SsoCliTools;
import com.aiassistant.todo.TodoWriteTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class ToolDispatcher {

    private final Map<String, ToolHandler> toolHandlers = new HashMap<>();

    @Autowired
    private CliExecutorTools cliExecutorTools;

    @Autowired
    private FileTools fileTools;

    @Autowired
    private SsoCliTools ssoCliTools;

    @Autowired
    private TodoWriteTools todoWriteTools;

    @PostConstruct
    public void initialize() {
        registerTools(cliExecutorTools);
        registerTools(fileTools);
        registerTools(ssoCliTools);
        registerTools(todoWriteTools);
        log.info("已注册 {} 个工具", toolHandlers.size());
    }

    private void registerTools(Object toolInstance) {
        for (Method method : toolInstance.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                dev.langchain4j.agent.tool.Tool toolAnnotation = method.getAnnotation(dev.langchain4j.agent.tool.Tool.class);
                String[] values = toolAnnotation.value();
                String toolName = values.length > 0 ? values[0] : method.getName();
                
                String mappedName = extractToolName(toolName);
                
                String[] paramNames = extractParamNames(method);
                toolHandlers.put(mappedName.toLowerCase(), new ToolHandler(toolInstance, method, paramNames));
                
                if (!mappedName.equalsIgnoreCase(method.getName())) {
                    toolHandlers.put(method.getName().toLowerCase(), new ToolHandler(toolInstance, method, paramNames));
                }
                
                toolHandlers.put(toolName.toLowerCase().replace(" ", ""), new ToolHandler(toolInstance, method, paramNames));
                
                log.debug("注册工具: {} -> {} (方法名: {}, 参数: {})", toolName, mappedName, method.getName(), 
                        paramNames != null ? String.join(",", paramNames) : "none");
            }
        }
    }
    
    private String[] extractParamNames(Method method) {
        java.lang.reflect.Parameter[] params = method.getParameters();
        String[] paramNames = new String[params.length];
        
        for (int i = 0; i < params.length; i++) {
            dev.langchain4j.agent.tool.P pAnnotation = params[i].getAnnotation(dev.langchain4j.agent.tool.P.class);
            if (pAnnotation != null) {
                String value = pAnnotation.value();
                if (value != null && !value.isEmpty()) {
                    paramNames[i] = value;
                } else {
                    paramNames[i] = params[i].getName();
                }
            } else {
                paramNames[i] = params[i].getName();
            }
        }
        
        return paramNames;
    }

    private String extractToolName(String description) {
        if (description.contains("执行CLI命令")) {
            return "executeCliCommand";
        } else if (description.contains("获取SKILL")) {
            return "getSkillContent";
        } else if (description.contains("加载技能")) {
            return "loadSkill";
        } else if (description.contains("创建Excel")) {
            return "createExcelFile";
        } else if (description.contains("读取Excel")) {
            return "readExcelFile";
        } else if (description.contains("创建Markdown")) {
            return "createMarkdownFile";
        } else if (description.contains("读取Markdown")) {
            return "readMarkdownFile";
        } else if (description.contains("创建TXT")) {
            return "createTxtFile";
        } else if (description.contains("读取TXT")) {
            return "readTxtFile";
        } else if (description.contains("创建待办任务")) {
            return "todoWrite";
        } else if (description.contains("查看待办任务")) {
            return "todoList";
        } else if (description.contains("更新待办任务")) {
            return "todoUpdate";
        } else if (description.contains("清空所有待办")) {
            return "todoClear";
        }
        return description;
    }

    public String executeTool(String toolName, Map<String, Object> parameters) {
        ToolHandler handler = toolHandlers.get(toolName.toLowerCase());
        if (handler == null) {
            return "错误：未知工具 '" + toolName + "'";
        }
        
        try {
            Object[] args = buildArguments(handler, parameters);
            Object result = handler.method().invoke(handler.instance(), args);
            return result != null ? result.toString() : "工具执行成功，无返回结果";
        } catch (Exception e) {
            log.error("工具执行失败: {}", e.getMessage());
            return "工具执行失败: " + e.getMessage();
        }
    }

    private Object[] buildArguments(ToolHandler handler, Map<String, Object> parameters) {
        java.lang.reflect.Parameter[] methodParams = handler.method().getParameters();
        String[] paramNames = handler.paramNames();
        Object[] args = new Object[methodParams.length];
        
        for (int i = 0; i < methodParams.length; i++) {
            java.lang.reflect.Parameter param = methodParams[i];
            String paramName = paramNames != null && i < paramNames.length ? paramNames[i] : param.getName();
            
            Object value = parameters.get(paramName);
            
            if (value == null) {
                value = parameters.get(paramName.toLowerCase());
            }
            
            if (value == null && paramName.equals("sessionId")) {
                value = com.aiassistant.common.SessionContext.getStaticMemoryId();
            }
            
            if (value == null && paramName.equals("filepath")) {
                value = parameters.get("filename");
            }
            
            if (value == null && paramName.equals("dataCode")) {
                value = parameters.get("command");
            }
            
            if (value == null && paramName.equals("skillName")) {
                value = parameters.get("command");
            }
            
            if (value == null && paramName.equals("技能名称")) {
                value = parameters.get("command");
            }
            
            if (value == null && paramName.equals("CLI命令字符串")) {
                value = parameters.get("command");
            }
            
            if (value == null && paramName.equals("文件路径")) {
                value = parameters.get("command");
                if (value == null) {
                    value = parameters.get("filepath");
                }
                if (value == null) {
                    value = parameters.get("filename");
                }
            }
            
            if (value == null && paramName.equals("内容")) {
                value = parameters.get("content");
                if (value == null) {
                    value = parameters.get("command");
                }
            }
            
            if (value == null && paramName.equals("表头")) {
                value = parameters.get("headers");
            }
            
            if (value == null && paramName.equals("数据行")) {
                value = parameters.get("rows");
            }
            
            if (value != null) {
                args[i] = convertValue(value, param.getType());
            } else {
                args[i] = null;
            }
        }
        
        return args;
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) return null;
        
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
        
        return value;
    }

    public Map<String, String> listTools() {
        Map<String, String> tools = new HashMap<>();
        for (Map.Entry<String, ToolHandler> entry : toolHandlers.entrySet()) {
            Method method = entry.getValue().method();
            dev.langchain4j.agent.tool.Tool annotation = method.getAnnotation(dev.langchain4j.agent.tool.Tool.class);
            String[] values = annotation.value();
            String desc = values.length > 0 ? values[0] : entry.getKey();
            tools.put(entry.getKey(), desc);
        }
        return tools;
    }

    public record ToolHandler(Object instance, Method method, String[] paramNames) {}
}