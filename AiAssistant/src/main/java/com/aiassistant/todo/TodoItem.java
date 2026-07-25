package com.aiassistant.todo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TodoItem {
    private String id;
    private String content;
    private TodoStatus status;
    private int priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}