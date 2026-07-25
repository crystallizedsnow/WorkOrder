package com.aiassistant.todo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@Slf4j
public class TodoManager {

    private static final int MAX_TODOS_PER_SESSION = 20;

    private final ConcurrentHashMap<Long, List<TodoItem>> sessionTodos = new ConcurrentHashMap<>();

    public List<TodoItem> getTodos(Long sessionId) {
        return sessionTodos.getOrDefault(sessionId, Collections.emptyList());
    }

    public TodoItem addTodo(Long sessionId, String content, int priority) {
        sessionTodos.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()));
        
        List<TodoItem> todos = sessionTodos.get(sessionId);
        if (todos.size() >= MAX_TODOS_PER_SESSION) {
            throw new RuntimeException("任务数量已达上限");
        }

        TodoItem item = TodoItem.builder()
                .id(UUID.randomUUID().toString().substring(0, 8))
                .content(content)
                .status(TodoStatus.PENDING)
                .priority(priority)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        todos.add(item);
        log.debug("添加任务 - sessionId: {}, id: {}, content: {}", sessionId, item.getId(), content);
        return item;
    }

    public TodoItem updateTodoStatus(Long sessionId, String todoId, TodoStatus status) {
        List<TodoItem> todos = sessionTodos.get(sessionId);
        if (todos == null) {
            throw new RuntimeException("会话不存在");
        }

        if (status == TodoStatus.IN_PROGRESS) {
            for (TodoItem item : todos) {
                if (item.getStatus() == TodoStatus.IN_PROGRESS) {
                    item.setStatus(TodoStatus.PENDING);
                    item.setUpdatedAt(LocalDateTime.now());
                }
            }
        }

        for (TodoItem item : todos) {
            if (item.getId().equals(todoId)) {
                item.setStatus(status);
                item.setUpdatedAt(LocalDateTime.now());
                log.debug("更新任务状态 - sessionId: {}, id: {}, status: {}", sessionId, todoId, status);
                return item;
            }
        }

        throw new RuntimeException("任务不存在");
    }

    public void clearTodos(Long sessionId) {
        sessionTodos.remove(sessionId);
        log.debug("清空任务列表 - sessionId: {}", sessionId);
    }

    public String getTodoSummary(Long sessionId) {
        List<TodoItem> todos = getTodos(sessionId);
        if (todos.isEmpty()) {
            return "当前没有待办任务";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("待办任务列表:\n");

        List<TodoItem> pending = todos.stream()
                .filter(t -> t.getStatus() == TodoStatus.PENDING)
                .sorted(Comparator.comparingInt(TodoItem::getPriority).reversed())
                .collect(Collectors.toList());

        List<TodoItem> inProgress = todos.stream()
                .filter(t -> t.getStatus() == TodoStatus.IN_PROGRESS)
                .collect(Collectors.toList());

        List<TodoItem> completed = todos.stream()
                .filter(t -> t.getStatus() == TodoStatus.COMPLETED)
                .collect(Collectors.toList());

        if (!inProgress.isEmpty()) {
            sb.append("\n🔄 进行中:\n");
            for (TodoItem item : inProgress) {
                sb.append(String.format("  [%s] %s\n", item.getId(), item.getContent()));
            }
        }

        if (!pending.isEmpty()) {
            sb.append("\n📋 待处理:\n");
            for (TodoItem item : pending) {
                sb.append(String.format("  [%s] %s (优先级: %d)\n", item.getId(), item.getContent(), item.getPriority()));
            }
        }

        if (!completed.isEmpty()) {
            sb.append("\n✅ 已完成:\n");
            for (TodoItem item : completed) {
                sb.append(String.format("  [%s] %s\n", item.getId(), item.getContent()));
            }
        }

        return sb.toString();
    }

    public boolean hasActiveTodos(Long sessionId) {
        List<TodoItem> todos = getTodos(sessionId);
        return todos != null && todos.stream()
                .anyMatch(t -> t.getStatus() != TodoStatus.COMPLETED);
    }

    public int getInProgressCount(Long sessionId) {
        List<TodoItem> todos = getTodos(sessionId);
        if (todos == null) {
            return 0;
        }
        return (int) todos.stream()
                .filter(t -> t.getStatus() == TodoStatus.IN_PROGRESS)
                .count();
    }
}