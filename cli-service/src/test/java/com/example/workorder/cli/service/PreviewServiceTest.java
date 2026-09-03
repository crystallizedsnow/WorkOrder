package com.example.workorder.cli.service;

import com.example.workorder.cli.dto.response.PreviewPlanDTO;
import com.example.workorder.cli.enums.WriteDataCodeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewServiceTest {

    private PreviewService previewService;

    @BeforeEach
    void setUp() {
        SchemaService schemaService = new SchemaService();
        schemaService.init();
        previewService = new PreviewService(
                schemaService, new PreviewMetadataRegistry(), new PreviewValueTranslator());
        previewService.validateConfiguration();
    }

    @Test
    void everyWriteDataCodeHasACompletePreview() {
        for (WriteDataCodeEnum operation : WriteDataCodeEnum.values()) {
            PreviewPlanDTO plan = previewService.preview(operation.getDataCode(), Map.of());
            assertNotNull(plan, operation.getDataCode());
            assertEquals(operation.getDataCode(), plan.getOperation());
            assertEquals(operation.getEndpoint(), plan.getEndpoint());
            assertFalse(plan.getOperationCN().isBlank());
            assertFalse(plan.getImpact().isBlank());
            assertFalse(plan.getRiskLevel().isBlank());
            assertTrue(plan.isExecutable());
        }
    }

    @Test
    void translatesValuesAndUsesSchemaDescriptions() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", 1);
        params.put("priorityLevel", 2);
        params.put("flowId", 9L);

        PreviewPlanDTO plan = previewService.preview("work_order_create", params);

        assertEquals("创建工单", plan.getOperationCN());
        assertEquals("1(故障)", plan.getReadableParams().get(0).getDisplayValue());
        assertEquals("2(低)", plan.getReadableParams().get(1).getDisplayValue());
        assertEquals("9(流程ID)", plan.getReadableParams().get(2).getDisplayValue());
        assertFalse(plan.getReadableParams().get(0).getDescription().isBlank());
    }

    @Test
    void preservesNestedFlowParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("flowName", "测试流程");
        params.put("nodes", java.util.List.of(Map.of("handlerId", 1, "handlerName", "张三")));

        PreviewPlanDTO plan = previewService.preview("flow_create", params);

        assertEquals(params, plan.getParams());
        assertEquals(2, plan.getReadableParams().size());
    }

    @Test
    void rejectsUnknownOrReadDataCode() {
        assertNull(previewService.preview("not_found", Map.of()));
        assertNull(previewService.preview("work_order_page", Map.of()));
    }
}
