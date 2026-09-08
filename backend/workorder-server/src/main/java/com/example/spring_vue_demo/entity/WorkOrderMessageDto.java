package com.example.spring_vue_demo.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author wtt
 * @date 2025/06/17
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkOrderMessageDto {
    @Schema(description = "关联工单id")
    private Long workOrderId;

    @Schema(description = "状态，100未审核，200审核中，270审核失败，300未派单，400处理中，410已超时，500已完成，600已确认完成，670确认失败，700已取消")
    private Integer status;

    @Schema(description = "工单编号")
    private String workOrderCode;

    @Schema(description = "接收人ids")
    private List<Long> receiverIds;

    @Schema(description = "发送人id")
    private Long senderId;

    @Schema(description = "是否完成")
    private Boolean finished;
}
