package com.aiassistant.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@Component
@ConfigurationProperties(prefix = "workorder.cli")
@Data
public class CliExecutorTools {

    private Map<String, String> tools = new HashMap<>();

    private int timeoutSeconds = 30;

    private String skillPath = "skills";

    @Tool("执行CLI命令，返回JSON格式结果。命令参数应为完整的CLI命令字符串，如 'workorder-cli work_order_page --pageNum 1 --pageSize 10'。")
    public String executeCliCommand(@P("CLI命令字符串") String command) throws IOException {
        String traceId = java.util.UUID.randomUUID().toString();

        log.info("executeCliCommand - 命令: {}", command);

        try {
            List<String> cmdArgs = buildCommandArgs(command);
            if (cmdArgs.isEmpty()) {
                return "{\"code\": 4, \"message\": \"命令格式错误\", \"data\": null, \"traceId\": \"" + traceId + "\"}";
            }

            String cliBinary = cmdArgs.get(0);
            log.info("executeCliCommand - CLI路径: {}", cliBinary);

            ProcessBuilder processBuilder = new ProcessBuilder(cmdArgs);

            processBuilder.environment().put("WORKORDER_TRACE_ID", traceId);

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<String> outputFuture = executor.submit(() -> {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder output = new StringBuilder();
                String line;
                try {
                    while ((line = reader.readLine()) != null) {
                        output.append(line);
                    }
                } catch (IOException e) {
                    log.warn("读取进程输出失败: {}", e.getMessage());
                }
                return output.toString();
            });

            boolean completed = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);

            String result = outputFuture.get();

            if (!completed) {
                process.destroyForcibly();
                throw new RuntimeException("CLI命令执行超时");
            }

            int exitCode = process.exitValue();

            if (exitCode != 0) {
                log.error("CLI命令执行失败，退出码: {}, 输出: {}", exitCode, result);
                return wrapErrorResult(exitCode, result);
            }

            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("CLI命令执行被中断");
        } catch (java.util.concurrent.ExecutionException e) {
            throw new RuntimeException("读取CLI输出失败: " + e.getCause().getMessage());
        }
    }

    @Tool("获取所有可用的CLI命令列表")
    public String listCliCommands() throws IOException {
        return executeCliCommand("workorder-cli list");
    }

    @Tool("获取指定CLI命令的参数Schema，如 'work_order_page'")
    public String getCliCommandSchema(@P("dataCode") String dataCode) throws IOException {
        return executeCliCommand("workorder-cli schema " + dataCode);
    }

    @Tool("获取SKILL.md技能文档内容，了解CLI工具调用方式和规则")
    public String getSkillContent() throws IOException {
        try {
            ClassPathResource resource = new ClassPathResource("SKILL.md");
            if (resource.exists()) {
                return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            }
            throw new RuntimeException("SKILL.md文件不存在");
        } catch (IOException e) {
            log.error("读取SKILL.md失败: {}", e.getMessage());
            throw new RuntimeException("读取SKILL.md失败: " + e.getMessage());
        }
    }

    @Tool("按需加载指定技能的完整内容，参数为技能名称，如 'workorder-query'")
    public String loadSkill(@P("技能名称") String skillName) throws IOException {
        String skillFilePath = skillPath + "/" + skillName + "/SKILL.md";
        try {
            ClassPathResource resource = new ClassPathResource(skillFilePath);
            if (resource.exists()) {
                return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            }
            throw new RuntimeException("技能文件不存在: " + skillFilePath);
        } catch (IOException e) {
            log.error("读取技能文件失败: {}", e.getMessage());
            throw new RuntimeException("读取技能文件失败: " + e.getMessage());
        }
    }

    private List<String> buildCommandArgs(String command) {
        List<String> args = new ArrayList<>();

        List<String> tokens = tokenizeCommand(command);
        if (tokens.isEmpty()) {
            return args;
        }

        String toolName = tokens.get(0);
        String cliPath = tools.get(toolName);

        if (cliPath == null) {
            log.warn("未找到CLI工具 '{}' 的配置，使用原名称", toolName);
            cliPath = toolName;
        }

        args.add(cliPath);

        for (int i = 1; i < tokens.size(); i++) {
            args.add(tokens.get(i));
        }

        return args;
    }

    private List<String> tokenizeCommand(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken = new StringBuilder();
                }
            } else {
                currentToken.append(c);
            }
        }

        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }

        return tokens;
    }

    private String wrapErrorResult(int exitCode, String message) {
        try {
            if (message != null && message.trim().startsWith("{")) {
                return message.trim();
            }
        } catch (Exception e) {
            log.warn("解析错误输出失败: {}", e.getMessage());
        }

        String errorMsg = message;
        switch (exitCode) {
            case 2:
                errorMsg = "认证错误: " + (message != null ? message : "Token无效或未提供");
                break;
            case 3:
                errorMsg = "超时错误: " + (message != null ? message : "命令执行超时");
                break;
            case 4:
                errorMsg = "参数错误: " + (message != null ? message : "参数格式不正确");
                break;
            default:
                errorMsg = "执行错误: " + (message != null ? message : "未知错误");
        }

        return String.format("{\"code\": %d, \"message\": \"%s\", \"data\": null, \"traceId\": \"\"}", exitCode, errorMsg);
    }
}
