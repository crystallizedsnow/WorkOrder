package com.example.spring_vue_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("notification_delivery")
public class NotificationDelivery {
    @TableId(type = IdType.AUTO) private Long id;
    private String eventId;
    private String eventType;
    private Long workOrderId;
    private String workOrderCode;
    private Long receiverId;
    private String channel;
    private String status;
    private Integer attempts;
    private LocalDateTime nextRetryTime;
    private LocalDateTime sendingStartedAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
}
