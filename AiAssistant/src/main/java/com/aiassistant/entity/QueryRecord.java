package com.aiassistant.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryRecord {

    private String query;

    private String result;

    private LocalDateTime timestamp;

    private Long userId;
}
