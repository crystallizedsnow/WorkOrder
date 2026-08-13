package com.example.spring_vue_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("channel_binding_challenge")
public class ChannelBindingChallenge {
    @TableId(type = IdType.INPUT) private String id;
    private Long userId;
    private String platform;
    private String codeHash;
    private String status;
    private Integer attempts;
    private Integer maxAttempts;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private LocalDateTime createTime;
}
