package com.example.spring_vue_demo.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author wtt
 * @date 2025/06/01
 */
@Data
public class HandleUserInfoVO {
    @TableId(type = IdType.AUTO)
    @Schema(description = "id")
    private Long id;

    @Schema(description = "工单id")
    private Long orderId;

    @Schema(description = "用户id")
    private Long userId;

    @Schema(description = "处理类型（1：提交人，2：审核人，3：派单人，4：处理人，5：确认人")
    private Integer handleType;

    @Schema(description = "处理类型desc")
    private String handleTypeDesc;

    @Schema(description = "已完成操作")
    private Boolean finished;

    @Schema(description = "已完成操作desc")
    private String finishedDesc;

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
}
