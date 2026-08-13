package com.example.spring_vue_demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("external_identity_binding")
public class ExternalIdentityBinding {
    @TableId(type = IdType.AUTO) private Long id;
    private String platform;
    private String tenantKey;
    private String unionId;
    private String openId;
    private Long userId;
    private String status;
    private Integer bindingVersion;
    private LocalDateTime boundAt;
    private LocalDateTime unboundAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
