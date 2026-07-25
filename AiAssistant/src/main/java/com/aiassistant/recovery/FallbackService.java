package com.aiassistant.recovery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Component
@Slf4j
public class FallbackService {

    public <T> T executeWithFallback(List<Supplier<T>> fallbackChain, String taskName) {
        List<Exception> exceptions = new ArrayList<>();

        for (int i = 0; i < fallbackChain.size(); i++) {
            Supplier<T> fallback = fallbackChain.get(i);
            try {
                T result = fallback.get();
                if (result != null) {
                    if (i > 0) {
                        log.info("任务 {} 主策略失败，兜底策略 {} 成功", taskName, i);
                    }
                    return result;
                }
            } catch (Exception e) {
                exceptions.add(e);
                log.debug("任务 {} 兜底策略 {} 失败: {}", taskName, i, e.getMessage());
            }
        }

        log.warn("任务 {} 所有兜底策略均失败", taskName);
        return null;
    }

    public String getErrorMessageForUser(ErrorType errorType, String errorMessage) {
        switch (errorType) {
            case USER_ACTIONABLE:
                if (errorMessage.contains("401") || errorMessage.contains("token")) {
                    return "您的登录已过期，请重新登录。使用格式: login('用户名', '密码')";
                }
                if (errorMessage.contains("403") || errorMessage.contains("permission")) {
                    return "您的权限不足，无法执行此操作。请联系管理员。";
                }
                return "需要您进行操作才能继续，请检查相关权限或重新登录。";
            case TRANSIENT:
                return "服务暂时不可用，请稍后重试。";
            case PERMANENT:
            default:
                return "执行失败: " + errorMessage;
        }
    }
}