package com.aiassistant.tools;

import com.aiassistant.common.SessionContext;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
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

    public SsoCliTools() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    @Tool("登录工单系统，返回登录结果。参数：phone（手机号），password（密码）")
    public String login(@P("手机号") String phone, @P("密码") String password) {
        if (phone == null || password == null) {
            return "错误：缺少必要参数。请提供 phone 和 password。";
        }

        try {
            String token = performLogin(phone, password);
            
            if (token != null) {
                tokenFileManager.saveToken(token, phone);
                return String.format("登录成功！手机号：%s", phone);
            } else {
                return "登录失败：用户名或密码错误。";
            }
        } catch (Exception e) {
            log.error("登录失败", e);
            return "登录失败：" + e.getMessage();
        }
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
        String token = SessionContext.getStaticToken();
        
        if (token == null) {
            TokenFileManager.TokenInfo tokenInfo = tokenFileManager.getToken();
            if (tokenInfo != null) {
                token = tokenInfo.getToken();
            }
        }
        
        if (token != null && !token.isEmpty()) {
            return "已配置Token";
        } else {
            return "未配置Token";
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
            
            if (response != null && response.contains("\"code\": 1")) {
                int dataStart = response.indexOf("\"data\": \"");
                if (dataStart >= 0) {
                    int tokenStart = dataStart + 9;
                    int tokenEnd = response.indexOf("\"", tokenStart);
                    if (tokenEnd > tokenStart) {
                        return response.substring(tokenStart, tokenEnd);
                    }
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