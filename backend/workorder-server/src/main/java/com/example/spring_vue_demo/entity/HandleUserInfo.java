package com.example.spring_vue_demo.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author wtt
 * @date 2025/05/25
 */
@Data
@TableName(value="handle_user_info",autoResultMap = true)
@AllArgsConstructor
@NoArgsConstructor
public class HandleUserInfo {

    @TableId(type = IdType.AUTO)
    @Schema(description = "id")
    private Long id;

    @Schema(description = "工单id")
    private Long orderId;

    @Schema(description = "用户id")
    private Long userId;

    @Schema(description = "处理类型（1：提交，2：审核，3：派单，4：处理，5：确认")
    private Integer handleType;

    @Schema(description = "已完成操作")
    private Boolean finished;

    @Schema(description = "姓名")
    private String userName;

    @Schema(description = "公司编码")
    private String companyCode;

    @Schema(description = "公司名")
    private String companyName;

    @Schema(description = "部门编码")
    private String departmentCode;

    @Schema(description = "部门名")
    private String departmentName;

    @Schema(description = "操作时间")
    private LocalDateTime handleTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "操作备注")
    private String remark;

    @TableLogic
    @Schema(description = "删除位")
    private Integer deleted;
}
