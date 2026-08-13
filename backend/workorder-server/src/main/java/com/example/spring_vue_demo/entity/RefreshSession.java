package com.example.spring_vue_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("auth_refresh_session")
public class RefreshSession {
    @TableId(type = IdType.INPUT)
    private String id;
    private String familyId;
    private Long userId;
    private String tokenHash;
    private Integer authVersion;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private LocalDateTime revokedAt;
    private String replacedBySessionId;
    private LocalDateTime createTime;
}
