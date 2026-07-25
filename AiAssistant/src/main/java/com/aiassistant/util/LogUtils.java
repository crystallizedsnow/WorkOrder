package com.aiassistant.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public class LogUtils {

    private static final Logger logger = LoggerFactory.getLogger(LogUtils.class);
    private static final int MAX_LENGTH = 2000;
    private static final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        try {
            String json = objectMapper.writeValueAsString(obj);
            if (json.length() > MAX_LENGTH) {
                return json.substring(0, MAX_LENGTH) + "...(truncated)";
            }
            return json;
        } catch (Exception e) {
            return obj.toString();
        }
    }

    public static void entrance(String traceId, String api, Object params) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("traceId", traceId);
        logData.put("api", api);
        logData.put("params", toJson(params));
        logData.put("type", "entrance");
        logger.info("[entranceLog] {}", toJson(logData));
    }

    public static void entrance(String traceId, String api, String method, Object params) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("traceId", traceId);
        logData.put("api", api);
        logData.put("method", method);
        logData.put("params", toJson(params));
        logData.put("type", "entrance");
        logger.info("[entranceLog] {}", toJson(logData));
    }

    public static void returnLog(String traceId, String api, Object result) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("traceId", traceId);
        logData.put("api", api);
        logData.put("result", toJson(result));
        logData.put("type", "return");
        logger.info("[returnLog] {}", toJson(logData));
    }

    public static void returnLog(String traceId, String api, String method, Object result) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("traceId", traceId);
        logData.put("api", api);
        logData.put("method", method);
        logData.put("result", toJson(result));
        logData.put("type", "return");
        logger.info("[returnLog] {}", toJson(logData));
    }

    public static void error(String traceId, String api, Object params, Throwable e) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("traceId", traceId);
        logData.put("api", api);
        logData.put("params", toJson(params));
        logData.put("error", e.getMessage());
        logData.put("type", "error");
        logger.error("[ERROR] {}", toJson(logData), e);
    }

    public static void error(String traceId, String api, String message) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("traceId", traceId);
        logData.put("api", api);
        logData.put("error", message);
        logData.put("type", "error");
        logger.error("[ERROR] {}", toJson(logData));
    }

    public static void warn(String traceId, String api, String message) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("traceId", traceId);
        logData.put("api", api);
        logData.put("message", message);
        logData.put("type", "warn");
        logger.warn("[WARN] {}", toJson(logData));
    }

    public static void sql(String traceId, String sql, Object params, Object result) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("traceId", traceId);
        logData.put("sql", sql);
        logData.put("params", toJson(params));
        logData.put("result", toJson(result));
        logData.put("type", "sql");
        logger.debug("[SQL Log] {}", toJson(logData));
    }

    public static void info(String traceId, String type, String message) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("traceId", traceId);
        logData.put("type", type);
        logData.put("message", message);
        logger.info("[{}] {}", type, toJson(logData));
    }

    public static void info(String traceId, String type, String message, Object data) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("traceId", traceId);
        logData.put("type", type);
        logData.put("message", message);
        logData.put("data", toJson(data));
        logger.info("[{}] {}", type, toJson(logData));
    }
}