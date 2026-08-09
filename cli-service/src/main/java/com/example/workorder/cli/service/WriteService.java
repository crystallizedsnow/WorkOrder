package com.example.workorder.cli.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.workorder.cli.enums.WriteDataCodeEnum;
import com.example.workorder.cli.feign.FlowFeignClient;
import com.example.workorder.cli.feign.WorkOrderFeignClient;
import com.example.workorder.cli.util.LogUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class WriteService {

    private static final Logger auditLogger = LoggerFactory.getLogger("WRITE-AUDIT");

    @Autowired
    private AuthService authService;

    @Autowired
    private WorkOrderFeignClient workOrderFeignClient;

    @Autowired
    private FlowFeignClient flowFeignClient;

    public Object execute(String dataCode, Map<String, Object> params, String token, String traceId) {
        String mdcTraceId = MDC.get("traceId");
        Map<String, Object> logParams = new HashMap<>();
        logParams.put("dataCode", dataCode);
        logParams.put("params", params);
        logParams.put("token", "***");
        LogUtils.entrance(mdcTraceId, "WriteService.execute", logParams);

        WriteDataCodeEnum e = WriteDataCodeEnum.fromDataCode(dataCode);
        if (e == null) {
            LogUtils.warn(mdcTraceId, "WriteService.execute", "Unknown dataCode: " + dataCode);
            logAudit(mdcTraceId, token, dataCode, "unknown", params, "fail", "dataCode not found");
            return createError(404, "dataCode not found: " + dataCode, mdcTraceId);
        }

        try {
            logAudit(mdcTraceId, token, dataCode, e.getDescription(), params, "pending", "");

            ResponseEntity<?> response = callFeignClient(e, params, token, mdcTraceId);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object normalized = normalizeResponse(response.getBody());
                logAudit(mdcTraceId, token, dataCode, e.getDescription(), params, "success", "");
                LogUtils.returnLog(mdcTraceId, "WriteService.execute", normalized);
                return normalized;
            } else {
                String errorMsg = "Backend returned non-2xx: " + response.getStatusCode();
                LogUtils.error(mdcTraceId, "WriteService.execute", errorMsg);
                logAudit(mdcTraceId, token, dataCode, e.getDescription(), params, "fail", errorMsg);
                return createError(500, "Backend service error: " + response.getStatusCode(), mdcTraceId);
            }
        } catch (Exception ex) {
            LogUtils.error(mdcTraceId, "WriteService.execute", logParams, ex);
            logAudit(mdcTraceId, token, dataCode, e.getDescription(), params, "fail", ex.getMessage());
            return createError(500, "Execute failed: " + ex.getMessage(), mdcTraceId);
        }
    }

    private ResponseEntity<?> callFeignClient(WriteDataCodeEnum e, Map<String, Object> params, String token, String traceId) {
        switch (e) {
            case WORK_ORDER_CREATE:
                return workOrderFeignClient.createWorkOrder(token, traceId, params);
            case WORK_ORDER_HANDLE:
                return workOrderFeignClient.handleWorkOrder(token, traceId, params);
            case WORK_ORDER_DELETE:
                return workOrderFeignClient.deleteWorkOrder(token, traceId, params);
            case WORK_ORDER_CANCEL:
                return workOrderFeignClient.cancelWorkOrder(token, traceId, params);
            case WORK_ORDER_APPROVAL:
                return workOrderFeignClient.approvalWorkOrder(token, traceId, params);
            case FLOW_CREATE:
                return flowFeignClient.createFlow(token, traceId, params);
            case FLOW_EDIT:
                return flowFeignClient.editFlow(token, traceId, params);
            case FLOW_DELETE:
                return flowFeignClient.deleteFlow(token, traceId, params);
            default:
                throw new IllegalArgumentException("Unsupported dataCode: " + e.getDataCode());
        }
    }

    private void logAudit(String traceId, String token, String dataCode, String action, Map<String, Object> params, String status, String error) {
        String userId = authService.validateToken(token).getUserId();
        String paramsSummary = params != null ? JSON.toJSONString(params) : "{}";
        if (paramsSummary.length() > 500) {
            paramsSummary = paramsSummary.substring(0, 500) + "...";
        }
        auditLogger.info("[WRITE-AUDIT] traceId={} userId={} dataCode={} action={} params={} status={} error={}",
                traceId != null ? traceId : "",
                userId != null ? userId : "unknown",
                dataCode,
                action,
                paramsSummary,
                status,
                error != null ? error : "");
    }

    private Object normalizeResponse(Object responseBody) {
        try {
            String jsonStr = JSON.toJSONString(responseBody);
            JSONObject json = JSON.parseObject(jsonStr);

            if (json.containsKey("code") && json.containsKey("data")) {
                int code = json.getIntValue("code");
                if (code == 0 || code == 1) {
                    Object data = json.get("data");
                    if (data == null || "".equals(data) || "null".equals(data)) {
                        Map<String, Object> result = new HashMap<>();
                        result.put("code", (long) 0);
                        result.put("message", "success");
                        result.put("data", null);
                        result.put("traceId", "");
                        return result;
                    }
                    Map<String, Object> result = new HashMap<>();
                    result.put("code", (long) 0);
                    result.put("message", "success");
                    result.put("data", data);
                    result.put("traceId", json.getString("traceId"));
                    return result;
                } else {
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("code", (long) code);
                    String message = json.getString("message");
                    if (message == null) {
                        message = json.getString("msg");
                    }
                    errorResult.put("message", message);
                    errorResult.put("data", json.get("data"));
                    errorResult.put("traceId", json.getString("traceId"));
                    return errorResult;
                }
            }

            if (json.containsKey("code") || json.containsKey("msg")) {
                int code = json.getIntValue("code");
                Map<String, Object> result = new HashMap<>();
                result.put("code", (long) code);
                String message = json.getString("message");
                if (message == null) {
                    message = json.getString("msg");
                }
                result.put("message", message);
                result.put("data", json.get("data"));
                result.put("traceId", json.getString("traceId"));
                return result;
            }

            Map<String, Object> result = new HashMap<>();
            result.put("code", (long) 0);
            result.put("message", "success");
            result.put("data", json);
            result.put("traceId", "");
            return result;
        } catch (Exception e) {
            LogUtils.error(MDC.get("traceId"), "WriteService", "Failed to parse response as JSON, returning raw object", e);
            Map<String, Object> result = new HashMap<>();
            result.put("code", (long) 0);
            result.put("message", "success");
            result.put("data", responseBody);
            result.put("traceId", "");
            return result;
        }
    }

    private Map<String, Object> createSuccess(Object data, String traceId) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("message", "success");
        result.put("data", data != null ? data : "");
        result.put("traceId", traceId != null ? traceId : "");
        return result;
    }

    private Map<String, Object> createError(int code, String message, String traceId) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", code);
        result.put("message", message != null ? message : "Unknown error");
        result.put("data", "");
        result.put("traceId", traceId != null ? traceId : "");
        return result;
    }
}
