package com.aiassistant.rag;

import org.springframework.stereotype.Component;

import java.util.List;

/** 将依赖上文的短追问改写为可独立检索的查询，不改变交给最终模型的原始问题。 */
@Component
public class QueryContextualizer {
    private static final String[] CONTEXT_MARKERS = {"那", "这个", "它", "该", "上一个", "下一步", "呢", "怎么办"};

    public String contextualize(String currentQuery, List<String> recentUserMessages) {
        if (currentQuery == null || currentQuery.isBlank()) return currentQuery;
        if (!needsContext(currentQuery) || recentUserMessages == null || recentUserMessages.isEmpty()) return currentQuery.trim();
        for (int i = recentUserMessages.size() - 1; i >= 0; i--) {
            String previous = recentUserMessages.get(i);
            if (previous != null && !previous.isBlank() && !previous.equals(currentQuery)) {
                return "上文问题：" + previous.trim() + "\n当前追问：" + currentQuery.trim();
            }
        }
        return currentQuery.trim();
    }

    private boolean needsContext(String query) {
        if (query.trim().length() <= 12) return true;
        // 较长问题通常已包含完整实体；只对短句中的指代词触发上下文化。
        if (query.trim().length() <= 18) {
            for (String marker : CONTEXT_MARKERS) if (query.contains(marker)) return true;
        }
        return false;
    }
}
