package com.example.spring_vue_demo.vo.WorkOrder;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author wtt
 * @date 2025/06/04
 */
@Data
public class WorkOrderTodoVO {
    @Schema(description = "工单id")
    private Long id;

    @Schema(description = "工单编号")
    private String code;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "类型")
    private Integer type;

    @Schema(description = "类型desc")
    private String typeDesc;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "状态desc")
    private String statusDesc;

    @Schema(description = "创建时间")
    private String createTime;
}
