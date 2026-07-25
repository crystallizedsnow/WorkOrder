package com.example.workorder.cli.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.workorder.api.param.Flow.FlowIdParam;
import com.example.workorder.api.param.Flow.FlowPageParam;
import com.example.workorder.api.param.MessageParam;
import com.example.workorder.api.param.WorkOrder.WorkOrderDetailParam;
import com.example.workorder.api.param.WorkOrder.WorkOrderPageParam;
import com.example.workorder.cli.enums.DataCodeEnum;
import com.example.workorder.cli.feign.DashboardFeignClient;
import com.example.workorder.cli.feign.FlowFeignClient;
import com.example.workorder.cli.feign.WorkOrderFeignClient;
import com.example.workorder.cli.util.LogUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class QueryService {

    @Autowired
    private AuthService authService;

    @Autowired
    private WorkOrderFeignClient workOrderFeignClient;

    @Autowired
    private DashboardFeignClient dashboardFeignClient;

    @Autowired
    private FlowFeignClient flowFeignClient;

    public Object query(String dataCode, Map<String, Object> params, String token, String traceId) {
        String mdcTraceId = MDC.get("traceId");
        Map<String, Object> logParams = new HashMap<>();
        logParams.put("dataCode", dataCode);
        logParams.put("params", params);
        logParams.put("token", "***");
        LogUtils.entrance(mdcTraceId, "QueryService.query", logParams);

        DataCodeEnum e = DataCodeEnum.fromDataCode(dataCode);
        if (e == null) {
            LogUtils.warn(mdcTraceId, "QueryService.query", "Unknown dataCode: " + dataCode);
            return createError(404, "dataCode not found: " + dataCode, mdcTraceId);
        }

        if ("admin".equals(e.getPermission()) && !authService.hasAdminRole(token)) {
            LogUtils.warn(mdcTraceId, "QueryService.query", "Permission denied for dataCode: " + dataCode + ", requires admin role");
            return createError(403, "Permission denied: admin role required", mdcTraceId);
        }

        try {
            ResponseEntity<?> response = callFeignClient(e, params, token, mdcTraceId);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object normalized = normalizeResponse(response.getBody());
                LogUtils.returnLog(mdcTraceId, "QueryService.query", normalized);
                return normalized;
            } else {
                LogUtils.error(mdcTraceId, "QueryService.query", "Backend returned non-2xx: " + response.getStatusCode());
                return createError(500, "Backend service error: " + response.getStatusCode(), mdcTraceId);
            }
        } catch (Exception ex) {
            LogUtils.error(mdcTraceId, "QueryService.query", logParams, ex);
            return createError(500, "Query failed: " + ex.getMessage(), mdcTraceId);
        }
    }

    private ResponseEntity<?> callFeignClient(DataCodeEnum e, Map<String, Object> params, String token, String traceId) {
        switch (e) {
            case WORK_ORDER_PAGE:
                WorkOrderPageParam pageParam = convertToPageParam(params);
                return workOrderFeignClient.pageWorkOrder(token, traceId, pageParam);
            case WORK_ORDER_DETAIL:
                WorkOrderDetailParam detailParam = convertToDetailParam(params);
                return workOrderFeignClient.detail(token, traceId, detailParam);
            case WORK_ORDER_SEARCH:
                String keyword = params != null ? String.valueOf(params.get("keyword")) : "";
                int pageNum = params != null && params.get("pageNum") != null 
                        ? ((Number) params.get("pageNum")).intValue() : 1;
                int pageSize = params != null && params.get("pageSize") != null 
                        ? ((Number) params.get("pageSize")).intValue() : 10;
                return workOrderFeignClient.searchWorkOrders(token, traceId, keyword, pageNum, pageSize);
            case DASHBOARD_DATA:
                return dashboardFeignClient.getData(token, traceId);
            case DASHBOARD_HANDLE_QUANTITY:
                return dashboardFeignClient.getHandleQuantity(token, traceId);
            case DASHBOARD_MESSAGES:
                MessageParam messageParam = convertToMessageParam(params);
                return dashboardFeignClient.pageMessages(token, traceId, messageParam);
            case FLOW_GET_BY_ID:
                FlowIdParam flowIdParam = convertToGetFlowByIdParam(params);
                return flowFeignClient.getById(token, traceId, flowIdParam);
            case FLOW_PAGE:
                FlowPageParam flowPageParam = convertToFlowPageParam(params);
                return flowFeignClient.page(token, traceId, flowPageParam);
            default:
                throw new IllegalArgumentException("Unsupported dataCode: " + e.getDataCode());
        }
    }

    private WorkOrderPageParam convertToPageParam(Map<String, Object> params) {
        if (params == null) return new WorkOrderPageParam();
        WorkOrderPageParam param = new WorkOrderPageParam();
        param.setPageNum(params.get("pageNum") != null ? ((Number) params.get("pageNum")).intValue() : 1);
        param.setPageSize(params.get("pageSize") != null ? ((Number) params.get("pageSize")).intValue() : 10);
        param.setId(params.get("id") != null ? ((Number) params.get("id")).longValue() : null);
        param.setCode(params.get("code") != null ? String.valueOf(params.get("code")) : null);
        param.setType(params.get("type") != null ? ((Number) params.get("type")).intValue() : null);
        param.setTitle(params.get("title") != null ? String.valueOf(params.get("title")) : null);
        param.setContent(params.get("content") != null ? String.valueOf(params.get("content")) : null);
        param.setPriorityLevel(params.get("priorityLevel") != null ? ((Number) params.get("priorityLevel")).intValue() : null);
        param.setStatus(params.get("status") != null ? (java.util.List<Integer>) params.get("status") : null);
        param.setCreateTimeFrom(params.get("createTimeFrom") != null ? ((Number) params.get("createTimeFrom")).longValue() : null);
        param.setCreateTimeTo(params.get("createTimeTo") != null ? ((Number) params.get("createTimeTo")).longValue() : null);
        param.setDeadLineFrom(params.get("deadLineFrom") != null ? ((Number) params.get("deadLineFrom")).longValue() : null);
        param.setDeadLineTo(params.get("deadLineTo") != null ? ((Number) params.get("deadLineTo")).longValue() : null);
        return param;
    }

    private WorkOrderDetailParam convertToDetailParam(Map<String, Object> params) {
        if (params == null) return new WorkOrderDetailParam();
        WorkOrderDetailParam param = new WorkOrderDetailParam();
        param.setId(params.get("id") != null ? ((Number) params.get("id")).longValue() : null);
        param.setCode(params.get("code") != null ? String.valueOf(params.get("code")) : null);
        return param;
    }

    private MessageParam convertToMessageParam(Map<String, Object> params) {
        if (params == null) return new MessageParam();
        MessageParam param = new MessageParam();
        param.setPageNum(params.get("pageNum") != null ? ((Number) params.get("pageNum")).intValue() : 1);
        param.setPageSize(params.get("pageSize") != null ? ((Number) params.get("pageSize")).intValue() : 10);
        return param;
    }

    private FlowIdParam convertToGetFlowByIdParam(Map<String, Object> params) {
        if (params == null) return new FlowIdParam();
        FlowIdParam param = new FlowIdParam();
        param.setFlowId(params.get("flowId") != null ? ((Number) params.get("flowId")).longValue() : null);
        return param;
    }

    private FlowPageParam convertToFlowPageParam(Map<String, Object> params) {
        if (params == null) return new FlowPageParam();
        FlowPageParam param = new FlowPageParam();
        param.setPageNum(params.get("pageNum") != null ? ((Number) params.get("pageNum")).intValue() : 1);
        param.setPageSize(params.get("pageSize") != null ? ((Number) params.get("pageSize")).intValue() : 10);
        return param;
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
                        Map<String, Object> result = new java.util.HashMap<>();
                        result.put("code", (long) 0);
                        result.put("message", "success");
                        result.put("data", null);
                        result.put("traceId", "");
                        return result;
                    }
                    Map<String, Object> result = new java.util.HashMap<>();
                    result.put("code", (long) 0);
                    result.put("message", "success");
                    result.put("data", data);
                    result.put("traceId", json.getString("traceId"));
                    return result;
                } else {
                    Map<String, Object> errorResult = new java.util.HashMap<>();
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
                Map<String, Object> result = new java.util.HashMap<>();
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

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("code", (long) 0);
            result.put("message", "success");
            result.put("data", json);
            result.put("traceId", "");
            return result;
        } catch (Exception e) {
            LogUtils.error(MDC.get("traceId"), "QueryService", "Failed to parse response as JSON, returning raw object", e);
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("code", (long) 0);
            result.put("message", "success");
            result.put("data", responseBody);
            result.put("traceId", "");
            return result;
        }
    }

    private Map<String, Object> createSuccess(Object data, String traceId) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("code", 0);
        result.put("message", "success");
        result.put("data", data != null ? data : "");
        result.put("traceId", traceId != null ? traceId : "");
        return result;
    }

    private Map<String, Object> createError(int code, String message, String traceId) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("code", code);
        result.put("message", message != null ? message : "Unknown error");
        result.put("data", "");
        result.put("traceId", traceId != null ? traceId : "");
        return result;
    }
}