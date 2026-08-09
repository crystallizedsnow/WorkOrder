package com.example.workorder.cli.enums;

import lombok.Getter;

@Getter
public enum WriteDataCodeEnum {

    WORK_ORDER_CREATE("work_order_create", "新建工单", "创建工单", "POST", "/workOrder/create", "all"),
    WORK_ORDER_HANDLE("work_order_handle", "工单处理", "处理工单（分配/协助/催单/完成/确认/仍有问题）", "POST", "/workOrder/handle", "all"),
    WORK_ORDER_DELETE("work_order_delete", "删除工单", "删除工单", "POST", "/workOrder/delete", "admin"),
    WORK_ORDER_CANCEL("work_order_cancel", "取消工单", "取消工单", "POST", "/workOrder/cancel", "all"),
    WORK_ORDER_APPROVAL("work_order_approval", "工单审批", "审批工单（通过/拒绝）", "POST", "/workOrder/approval", "all"),

    FLOW_CREATE("flow_create", "新增流程", "创建工单流程", "POST", "/flow/create", "admin"),
    FLOW_EDIT("flow_edit", "编辑流程", "编辑工单流程", "POST", "/flow/edit", "admin"),
    FLOW_DELETE("flow_delete", "删除流程", "删除工单流程", "POST", "/flow/delete", "admin");

    private final String dataCode;
    private final String name;
    private final String description;
    private final String httpMethod;
    private final String endpoint;
    private final String permission;

    WriteDataCodeEnum(String dataCode, String name, String description, String httpMethod, String endpoint, String permission) {
        this.dataCode = dataCode;
        this.name = name;
        this.description = description;
        this.httpMethod = httpMethod;
        this.endpoint = endpoint;
        this.permission = permission;
    }

    public static WriteDataCodeEnum fromDataCode(String dataCode) {
        for (WriteDataCodeEnum e : values()) {
            if (e.dataCode.equals(dataCode)) {
                return e;
            }
        }
        return null;
    }
}
