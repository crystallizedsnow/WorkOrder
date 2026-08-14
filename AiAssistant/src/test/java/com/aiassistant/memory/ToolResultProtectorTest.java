package com.aiassistant.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolResultProtectorTest {
    @Test void keepsStatusAndPaginationWhenLargeResultIsProtected() {
        ToolResultProtector protector = new ToolResultProtector(new TokenEstimator());
        ReflectionTestUtils.setField(protector, "maxTokens", 20);
        String result = protector.protect("{\"code\":0,\"total\":99,\"pageNum\":1,\"result\":\"" + "数据".repeat(100) + "\"}");
        assertTrue(result.contains("\"truncated\":true"));
        assertTrue(result.contains("\"code\":0"));
        assertTrue(result.contains("\"total\":99"));
        assertFalse(result.contains("数据数据数据数据数据"));
    }

    @Test void preservesCompactWorkOrderRowsFromNestedCliResult() throws Exception {
        ToolResultProtector protector = new ToolResultProtector(new TokenEstimator());
        ReflectionTestUtils.setField(protector, "maxTokens", 20);
        String nested = "{\"code\":0,\"data\":{\"total\":2,\"records\":["
                + "{\"id\":74,\"code\":\"WO-74\",\"title\":\"网络故障\",\"statusDesc\":\"处理中\",\"priorityLevelDesc\":\"高\",\"unused\":\"" + "冗余".repeat(100) + "\"},"
                + "{\"id\":75,\"code\":\"WO-75\",\"title\":\"打印机故障\",\"statusDesc\":\"待处理\"}]}}";
        String wrapped = new ObjectMapper().writeValueAsString(Map.of("tool", "executeCliCommand", "success", true, "result", nested));

        String result = protector.protect(wrapped);

        assertTrue(result.contains("\"retryable\":false"));
        assertTrue(result.contains("WO-74"));
        assertTrue(result.contains("网络故障"));
        assertTrue(result.contains("priorityLevelDesc"));
        assertFalse(result.contains("unused"));
    }
}
