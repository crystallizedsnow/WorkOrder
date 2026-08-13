package com.aiassistant.common;

import org.springframework.stereotype.Component;

@Component
public class SessionContext {

    private static final ThreadLocal<Long> currentMemoryId = new ThreadLocal<>();
    private static final ThreadLocal<String> currentToken = new ThreadLocal<>();
    private static final ThreadLocal<com.aiassistant.channel.model.AgentRequest> currentRequest = new ThreadLocal<>();
    
    public void setSession(Long memoryId, String token) {
        currentMemoryId.set(memoryId);
        currentToken.set(token);
    }

    public Long getMemoryId() {
        return currentMemoryId.get();
    }
    public void setRequest(com.aiassistant.channel.model.AgentRequest request) { currentRequest.set(request); }
    public com.aiassistant.channel.model.AgentRequest getRequest() { return currentRequest.get(); }

    public String getToken() {
        String token = currentToken.get();
        return currentToken.get();
    }

    public void clear() {
        currentMemoryId.remove();
        currentToken.remove();
        currentRequest.remove();
    }

    public static Long getStaticMemoryId() {
        return currentMemoryId.get();
    }

    public static String getStaticToken() {
        return currentToken.get();
    }

    public static void setStaticToken(String token) {
        currentToken.set(token);
    }

    public static void clearSession() {
        currentMemoryId.remove();
        currentToken.remove();
    }

    public static void clearToken() {
        currentToken.remove();
    }
    
    public static String getCachedToken() { return null; }
}
