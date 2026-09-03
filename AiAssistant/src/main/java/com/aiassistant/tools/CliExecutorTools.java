package com.aiassistant.tools;

import com.aiassistant.tool.annotation.P;
import com.aiassistant.tool.annotation.Tool;
import com.aiassistant.common.SessionContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.aiassistant.channel.confirmation.WriteConfirmationService;
import com.aiassistant.channel.ConfirmationNotifier;
import com.aiassistant.channel.model.AgentRequest;
import com.aiassistant.channel.model.ChannelType;

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
import java.util.regex.Pattern;

@Slf4j
@Component
@ConfigurationProperties(prefix = "workorder.cli")
@Data
public class CliExecutorTools {

    private static final Pattern PREVIEW_ID = Pattern.compile("预演凭证:\\s*(wp_[A-Za-z0-9]+)");

    @Autowired private SessionContext sessionContext;
    @Autowired private WriteConfirmationService confirmations;
    @Autowired private ConfirmationNotifier confirmationNotifier;

    private Map<String, String> tools = new HashMap<>();

    private int timeoutSeconds = 30;

    private String skillPath = "skills";

    @Tool("执行CLI命令，返回命令输出。只负责透明执行传入的完整CLI命令字符串；命令规范请严格遵循已加载的SKILL.md。示例：'workorder-cli work_order_page --page-num 1 --page-size 10'。")
    public String executeCliCommand(@P("CLI命令字符串") String command) throws IOException {
        String traceId = java.util.UUID.randomUUID().toString();

        List<String> requestedArgs = tokenizeCommand(command == null ? "" : command);
        if (containsCredentialOperation(requestedArgs)) {
            log.warn("拒绝 Agent 发起登录或携带凭证的 CLI 命令");
            return "{\"code\": 4, \"message\": \"禁止Agent执行登录、刷新凭证或在命令中传递密码/Token；请使用当前请求的Bearer Access Token\", \"data\": null, \"traceId\": \"" + traceId + "\"}";
        }

        log.info("executeCliCommand - 命令: {}", redactCommand(requestedArgs));

        try {
            List<String> cmdArgs = buildCommandArgs(command);
            if (cmdArgs.isEmpty()) {
                return "{\"code\": 4, \"message\": \"命令格式错误\", \"data\": null, \"traceId\": \"" + traceId + "\"}";
            }

            String cliBinary = cmdArgs.get(0);
            log.info("executeCliCommand - CLI路径: {}", cliBinary);

            CommandResult commandResult = runCommand(cmdArgs, traceId);
            String result = commandResult.output();
            int exitCode = commandResult.exitCode();

            if (exitCode != 0) {
                log.error("CLI命令执行失败，退出码: {}, 输出: {}", exitCode, result);
                return wrapErrorResult(exitCode, result);
            }

            registerPreview(command, result);
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("CLI命令执行被中断");
        } catch (java.util.concurrent.ExecutionException e) {
            throw new RuntimeException("读取CLI输出失败: " + e.getCause().getMessage());
        }
    }

    private CommandResult runCommand(List<String> cmdArgs, String traceId) throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        ProcessBuilder processBuilder = new ProcessBuilder(cmdArgs);
        processBuilder.environment().put("WORKORDER_TRACE_ID", traceId);
        String requestToken = SessionContext.getStaticToken();
        if (requestToken == null || requestToken.isBlank()) {
            throw new IllegalStateException("当前请求没有用户访问凭证");
        }
        processBuilder.environment().put("WORKORDER_TOKEN", requestToken);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
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
            if (!completed) {
                process.destroyForcibly();
                outputFuture.cancel(true);
                throw new RuntimeException("CLI命令执行超时");
            }

            String output = outputFuture.get();
            return new CommandResult(process.exitValue(), output);
        } finally {
            executor.shutdownNow();
        }
    }

    private void registerPreview(String command, String result) {
        if (command == null || !command.contains("--dry-run") || result == null) return;
        var matcher = PREVIEW_ID.matcher(result);
        if (!matcher.find()) return;
        AgentRequest request = sessionContext.getRequest();
        if (request == null || request.sessionId() == null || request.userId() == null) return;
        String requester = request.senderId() == null ? request.userId() : request.senderId();
        var pending = confirmations.createFromPreview(request.sessionId(), request.userId(), request.tenantId(),
                request.sourceConversationId(), requester, command, matcher.group(1), result, request.traceId());
        if (request.channel() == ChannelType.FEISHU) confirmationNotifier.notify(pending);
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
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

    private boolean containsCredentialOperation(List<String> args) {
        for (int i = 1; i < args.size(); i++) {
            String arg = args.get(i).toLowerCase(java.util.Locale.ROOT);
            if ("login".equals(arg) || "--password".equals(arg) || "--token".equals(arg) || "-t".equals(arg)) {
                return true;
            }
            if ("auth".equals(arg) && i + 1 < args.size()
                    && ("login".equalsIgnoreCase(args.get(i + 1)) || "refresh".equalsIgnoreCase(args.get(i + 1)))) {
                return true;
            }
        }
        return false;
    }

    private String redactCommand(List<String> args) {
        List<String> safe = new ArrayList<>(args);
        for (int i = 0; i < safe.size(); i++) {
            String arg = safe.get(i).toLowerCase(java.util.Locale.ROOT);
            if (("--password".equals(arg) || "--token".equals(arg) || "-t".equals(arg)) && i + 1 < safe.size()) {
                safe.set(i + 1, "***");
            }
        }
        return String.join(" ", safe);
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

        return String.format("{\"code\": %d, \"message\": \"%s\", \"data\": null, \"traceId\": \"\"}", exitCode, escapeJson(errorMsg));
    }

    private record CommandResult(int exitCode, String output) {}
}
