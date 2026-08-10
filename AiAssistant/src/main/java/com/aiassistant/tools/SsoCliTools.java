package com.aiassistant.tools;

import com.aiassistant.common.SessionContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiassistant.tool.annotation.P;
import com.aiassistant.tool.annotation.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Slf4j
public class SsoCliTools {

    @Autowired
    private TokenFileManager tokenFileManager;

    @Value("${workorder.backend.login-url:http://localhost:8080/user/login}")
    private String loginUrl;

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SsoCliTools() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    @Tool("登录工单系统，成功后将新Token写入 ~/.workorder/token，并保存账号到 ~/.workorder/account。参数：phone（手机号），password（密码）")
    public String login(@P("phone") String phone, @P("password") String password) {
        if (phone == null || phone.isBlank() || password == null || password.isBlank()) {
            return "错误：缺少必要参数。请提供 phone 和 password。";
        }

        try {
            String token = performLogin(phone, password);
            
            if (token != null) {
                tokenFileManager.saveToken(token, phone);
                tokenFileManager.saveAccount(phone, password);
                return String.format("登录成功！手机号：%s", phone);
            } else {
                return "登录失败：用户名或密码错误。";
            }
        } catch (Exception e) {
            log.error("登录失败", e);
            return "登录失败：" + e.getMessage();
        }
    }

    @Tool("从 ~/.workorder/account 读取已保存的phone/password并登录，成功后写入新的Token缓存。Token过期或CLI返回401时优先调用此工具。")
    public String loginFromStoredAccount() {
        TokenFileManager.AccountInfo accountInfo = tokenFileManager.getAccount();
        if (accountInfo == null) {
            return "登录失败：未找到账号文件 ~/.workorder/account，请提供 phone 和 password 后调用 login。";
        }
        if (accountInfo.getPhone() == null || accountInfo.getPhone().isBlank()
                || accountInfo.getPassword() == null || accountInfo.getPassword().isBlank()) {
            return "登录失败：账号文件缺少 phone 或 password，请提供 phone 和 password 后调用 login。";
        }
        return login(accountInfo.getPhone(), accountInfo.getPassword());
    }

    @Tool("获取当前登录Token信息")
    public String getToken() {
        String token = SessionContext.getStaticToken();
        
        if (token == null) {
            TokenFileManager.TokenInfo tokenInfo = tokenFileManager.getToken();
            if (tokenInfo != null) {
                token = tokenInfo.getToken();
            }
        }
        
        if (token == null) {
            return "未配置Token，请通过Authorization header提供或调用login登录。";
        }
        
        return String.format("Token：%s", token);
    }

    @Tool("检查当前登录状态")
    public String checkLoginStatus() {
        TokenFileManager.TokenInfo tokenInfo = tokenFileManager.getToken();
        if (tokenInfo != null && tokenInfo.getToken() != null && !tokenInfo.getToken().isEmpty()) {
            return "Token有效";
        }

        String sessionToken = SessionContext.getStaticToken();
        if (sessionToken != null && !sessionToken.isEmpty()) {
            return "已配置会话Token";
        } else {
            return "Token缺失或已过期";
        }
    }

    private String performLogin(String phone, String password) {
        try {
            String response = webClient.post()
                    .uri(loginUrl)
                    .header("Content-Type", "application/json")
                    .bodyValue("{\"phone\": \"" + phone + "\", \"password\": \"" + password + "\"}")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            if (response != null) {
                JsonNode node = objectMapper.readTree(response);
                if (node.path("code").asInt() == 1 && node.hasNonNull("data")) {
                    return node.path("data").asText();
                }
            }
            
            log.warn("登录响应: {}", response);
            return null;
        } catch (Exception e) {
            log.error("登录请求失败", e);
            return null;
        }
    }
}
