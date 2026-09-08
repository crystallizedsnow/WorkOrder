package com.example.workorder.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class MessageVO {
    @Schema(description = "消息id")
    private Long id;

    @Schema(description = "关联工单id")
    private Long workOrderId;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "消息类型")
    private Integer type;

    @Schema(description = "消息类型desc")
    private String typeDesc;

    @Schema(description = "发送时间")
    private String sendTime;
}
