package com.aiassistant.common;

import org.springframework.stereotype.Component;

@Component
public class SessionContext {

    private static final ThreadLocal<Long> currentMemoryId = new ThreadLocal<>();
    private static final ThreadLocal<String> currentToken = new ThreadLocal<>();
    
    private static volatile String cachedToken = null;

    public void setSession(Long memoryId, String token) {
        currentMemoryId.set(memoryId);
        currentToken.set(token);
        cachedToken = token;
    }

    public Long getMemoryId() {
        return currentMemoryId.get();
    }

    public String getToken() {
        String token = currentToken.get();
        return token != null ? token : cachedToken;
    }

    public void clear() {
        currentMemoryId.remove();
        currentToken.remove();
        cachedToken = null;
    }

    public static Long getStaticMemoryId() {
        return currentMemoryId.get();
    }

    public static String getStaticToken() {
        String token = currentToken.get();
        return token != null ? token : cachedToken;
    }

    public static void setStaticToken(String token) {
        currentToken.set(token);
        cachedToken = token;
    }

    public static void clearSession() {
        currentMemoryId.remove();
        currentToken.remove();
        cachedToken = null;
    }

    public static void clearToken() {
        currentToken.remove();
        cachedToken = null;
    }
    
    public static String getCachedToken() {
        return cachedToken;
    }
}
