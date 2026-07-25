package com.aiassistant.todo;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class TodoWriteTools {

    private final TodoManager todoManager;

    @Tool("创建待办任务")
    public String todoWrite(Long sessionId, String content, Integer priority) {
        if (priority == null) {
            priority = 1;
        }
        TodoItem item = todoManager.addTodo(sessionId, content, priority);
        return String.format("任务已创建: [%s] %s", item.getId(), content);
    }

    @Tool("查看待办任务列表")
    public String todoList(Long sessionId) {
        return todoManager.getTodoSummary(sessionId);
    }

    @Tool("更新待办任务状态")
    public String todoUpdate(Long sessionId, String todoId, String status) {
        TodoStatus todoStatus;
        switch (status.toLowerCase()) {
            case "in_progress":
            case "doing":
            case "进行中":
                todoStatus = TodoStatus.IN_PROGRESS;
                break;
            case "completed":
            case "done":
            case "已完成":
                todoStatus = TodoStatus.COMPLETED;
                break;
            case "pending":
            case "todo":
            case "待处理":
            default:
                todoStatus = TodoStatus.PENDING;
        }
        
        TodoItem item = todoManager.updateTodoStatus(sessionId, todoId, todoStatus);
        return String.format("任务状态已更新: [%s] %s -> %s", item.getId(), item.getContent(), todoStatus);
    }

    @Tool("清空所有待办任务")
    public String todoClear(Long sessionId) {
        todoManager.clearTodos(sessionId);
        return "所有任务已清空";
    }
}