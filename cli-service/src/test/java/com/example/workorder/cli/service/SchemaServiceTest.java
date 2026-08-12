package com.example.workorder.cli.service;

import com.example.workorder.cli.dto.response.SchemaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SchemaServiceTest {

    private SchemaService schemaService;

    @BeforeEach
    void setUp() {
        schemaService = new SchemaService();
        schemaService.init();
    }

    @Test
    void scalarFieldsExposeGeneratedCliFlags() {
        SchemaDTO schema = schemaService.getSchema("work_order_handle");

        assertNotNull(schema);
        Map<String, Object> assignedUser = field(schema, "assignedUserId");
        assertEquals("flag", assignedUser.get("cliTransport"));
        assertEquals("--assigned-user-id", assignedUser.get("cliFlag"));

        Map<String, Object> remark = field(schema, "remark");
        assertEquals("--remark", remark.get("cliFlag"));
    }

    @Test
    void structuredFieldsUseBodyTransportWithoutInventingFlags() {
        SchemaDTO schema = schemaService.getSchema("flow_create");

        Map<String, Object> nodes = field(schema, "nodes");
        assertEquals("body", nodes.get("cliTransport"));
        assertFalse(nodes.containsKey("cliFlag"));

        Map<String, Object> nestedHandler = field(schema, "nodes[].handlerId");
        assertEquals("body", nestedHandler.get("cliTransport"));
        assertFalse(nestedHandler.containsKey("cliFlag"));
    }

    @Test
    void approvalQueriesExposeActorsAndFlowNodes() {
        SchemaDTO detail = schemaService.getSchema("work_order_detail");
        assertNotNull(detail.getOutputSchema().get("auditorInfo[].userId"));

        SchemaDTO flow = schemaService.getSchema("flow_get_by_id");
        assertNotNull(flow.getOutputSchema().get("nodes[].handlerId"));
        assertNotNull(flow.getOutputSchema().get("nodes[].handlerName"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> field(SchemaDTO schema, String name) {
        return (Map<String, Object>) schema.getInputSchema().get(name);
    }
}
