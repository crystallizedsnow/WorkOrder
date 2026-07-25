package com.example.spring_vue_demo.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;



/**
 * @author wtt
 * @date 2025/06/14
 */
@Data
@TableName(value = "flow", autoResultMap = true)
@Schema(description = "工单流程定义表")
public class Flow {

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "id")
    private Long id;

    @Schema(description = "流程id")
    private Long flowId;

    @Schema(description = "流程名")
    private String flowName;

    @Schema(description = "节点类型:审批2，指派3，验收5")
    private Integer nodeType;

    @Schema(description = "处理人id")
    private Long handlerId;

    @Schema(description = "处理人名")
    private String handlerName;

    @Schema(description = "是否并行处理")
    private Integer isParallel;

    @Schema(description = "头节点id(只有审核有)")
    private Long headFlowId;

    @Schema(description = "下一节点id(只有审核有)")
    private Long nextFlowId;

    @Schema(description = "是否为该流程终止节点")
    private Boolean isLastNode;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}