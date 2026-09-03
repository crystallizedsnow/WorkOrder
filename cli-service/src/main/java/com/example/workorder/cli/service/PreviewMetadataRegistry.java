package com.example.workorder.cli.service;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PreviewMetadataRegistry {

    public record PreviewMetadata(String operationName, String riskLevel, String impact) {}

    private static final Map<String, PreviewMetadata> METADATA = Map.of(
            "work_order_create", new PreviewMetadata("创建工单", "medium", "新增一条工单记录，分配给处理人"),
            "work_order_handle", new PreviewMetadata("处理工单", "medium", "变更工单状态，可能通知相关人员"),
            "work_order_delete", new PreviewMetadata("删除工单", "high", "物理删除工单记录，不可恢复"),
            "work_order_cancel", new PreviewMetadata("取消工单", "medium", "将工单状态变更为已取消"),
            "work_order_approval", new PreviewMetadata("审批工单", "medium", "审批通过/拒绝，变更工单生命周期"),
            "flow_create", new PreviewMetadata("创建流程", "high", "新增流程定义，影响后续工单的处理流程"),
            "flow_edit", new PreviewMetadata("编辑流程", "high", "修改流程定义，可能影响使用该流程的工单"),
            "flow_delete", new PreviewMetadata("删除流程", "high", "删除流程定义，不可恢复")
    );

    public PreviewMetadata get(String dataCode) {
        return METADATA.get(dataCode);
    }
}
