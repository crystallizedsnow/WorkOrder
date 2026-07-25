package com.aiassistant.recovery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class ErrorClassifier {

    private static final List<String> TRANSIENT_KEYWORDS = Arrays.asList(
            "timeout", "connection refused", "network", "429", "too many requests",
            "service unavailable", "503", "502", "reset", "interrupted"
    );

    private static final List<String> USER_ACTIONABLE_KEYWORDS = Arrays.asList(
            "401", "unauthorized", "login", "token", "expired",
            "403", "forbidden", "permission", "access denied"
    );

    public ErrorType classify(Throwable error) {
        if (error == null) {
            return ErrorType.PERMANENT;
        }

        String message = error.getMessage();
        if (message == null) {
            message = error.getClass().getName();
        }

        String lowerMessage = message.toLowerCase();

        for (String keyword : USER_ACTIONABLE_KEYWORDS) {
            if (lowerMessage.contains(keyword.toLowerCase())) {
                log.debug("错误分类为USER_ACTIONABLE: {}", message);
                return ErrorType.USER_ACTIONABLE;
            }
        }

        for (String keyword : TRANSIENT_KEYWORDS) {
            if (lowerMessage.contains(keyword.toLowerCase())) {
                log.debug("错误分类为TRANSIENT: {}", message);
                return ErrorType.TRANSIENT;
            }
        }

        log.debug("错误分类为PERMANENT: {}", message);
        return ErrorType.PERMANENT;
    }

    public ErrorType classify(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return ErrorType.PERMANENT;
        }

        String lowerMessage = errorMessage.toLowerCase();

        for (String keyword : USER_ACTIONABLE_KEYWORDS) {
            if (lowerMessage.contains(keyword.toLowerCase())) {
                return ErrorType.USER_ACTIONABLE;
            }
        }

        for (String keyword : TRANSIENT_KEYWORDS) {
            if (lowerMessage.contains(keyword.toLowerCase())) {
                return ErrorType.TRANSIENT;
            }
        }

        return ErrorType.PERMANENT;
    }
}