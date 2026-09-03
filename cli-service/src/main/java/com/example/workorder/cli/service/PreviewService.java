package com.example.workorder.cli.service;

import com.example.workorder.cli.dto.response.PreviewPlanDTO;
import com.example.workorder.cli.dto.response.SchemaDTO;
import com.example.workorder.cli.enums.WriteDataCodeEnum;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Service
public class PreviewService {

    private final SchemaService schemaService;
    private final PreviewMetadataRegistry metadataRegistry;
    private final PreviewValueTranslator valueTranslator;

    public PreviewService(SchemaService schemaService,
                          PreviewMetadataRegistry metadataRegistry,
                          PreviewValueTranslator valueTranslator) {
        this.schemaService = schemaService;
        this.metadataRegistry = metadataRegistry;
        this.valueTranslator = valueTranslator;
    }

    @PostConstruct
    void validateConfiguration() {
        for (WriteDataCodeEnum operation : WriteDataCodeEnum.values()) {
            if (schemaService.getSchema(operation.getDataCode()) == null) {
                throw new IllegalStateException("Missing schema for write dataCode: " + operation.getDataCode());
            }
            if (metadataRegistry.get(operation.getDataCode()) == null) {
                throw new IllegalStateException("Missing preview metadata for write dataCode: " + operation.getDataCode());
            }
        }
    }

    public PreviewPlanDTO preview(String dataCode, Map<String, Object> params) {
        WriteDataCodeEnum operation = WriteDataCodeEnum.fromDataCode(dataCode);
        if (operation == null) {
            return null;
        }

        SchemaDTO schema = schemaService.getSchema(dataCode);
        PreviewMetadataRegistry.PreviewMetadata metadata = metadataRegistry.get(dataCode);
        Map<String, Object> safeParams = params == null ? Collections.emptyMap() : params;

        PreviewPlanDTO plan = new PreviewPlanDTO();
        plan.setOperation(operation.getDataCode());
        plan.setOperationCN(metadata.operationName());
        plan.setHttpMethod(operation.getHttpMethod());
        plan.setEndpoint(operation.getEndpoint());
        plan.setParams(safeParams);
        plan.setReadableParams(valueTranslator.translate(safeParams, schema.getInputSchema()));
        plan.setImpact(metadata.impact());
        plan.setRiskLevel(metadata.riskLevel());
        plan.setExecutable(true);
        plan.setSchemaVersion("1");
        return plan;
    }
}
