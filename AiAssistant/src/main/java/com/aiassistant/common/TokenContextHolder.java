package com.aiassistant.common;

import org.springframework.stereotype.Component;

@Component
public class TokenContextHolder {

    private static final ThreadLocal<String> currentToken = new ThreadLocal<>();

    public void setToken(String token) {
        currentToken.set(token);
    }

    public String getToken() {
        return currentToken.get();
    }

    public void clear() {
        currentToken.remove();
    }

    public static void setStaticToken(String token) {
        currentToken.set(token);
    }

    public static String getStaticToken() {
        return currentToken.get();
    }

    public static void clearToken() {
        currentToken.remove();
    }
}
