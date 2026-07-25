package com.aiassistant.hook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HookContext {
    private Long sessionId;
    private String userId;
    private String llmResponse;
    private String toolName;
    private String toolResult;
    private Integer tokenCount;
    private String errorMessage;
    private Map<String, Object> customData;
}