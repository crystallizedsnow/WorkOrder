package com.example.spring_vue_demo.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public class LogUtils {

    private static final Logger logger = LoggerFactory.getLogger(LogUtils.class);
    private static final int MAX_LENGTH = 2000;

    private static final String ENTRANCE_TEMPLATE = "[entranceLog] {}";
    private static final String RETURN_TEMPLATE = "[returnLog] {}";
    private static final String ERROR_TEMPLATE = "[ERROR] {}";
    private static final String WARN_TEMPLATE = "[WARN] {}";
    private static final String SQL_TEMPLATE = "[SQL Log] {}";

    private static String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        try {
            String json = JSON.toJSONString(obj, SerializerFeature.WriteMapNullValue, SerializerFeature.QuoteFieldNames);
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
        logger.info(ENTRANCE_TEMPLATE, toJson(logData));
    }

    public static void entrance(String traceId, String api, String method, Object params) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("traceId", traceId);
        logData.put("api", api);
        logData.put("method", method);
        logData.put("params", toJson(params));
        logData.put("type", "entrance");
        logger.info(ENTRANCE_TEMPLATE, toJson(logData));
    }

    public static void returnLog(String traceId, String api, Object result) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("traceId", traceId);
        logData.put("api", api);
        logData.put("result", toJson(result));
        logData.put("type", "return");
        logger.info(RETURN_TEMPLATE, toJson(logData));
    }

    public static void returnLog(String traceId, String api, String method, Object result) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("traceId", traceId);
        logData.put("api", api);
        logData.put("method", method);
        logData.put("result", toJson(result));
        logData.put("type", "return");
        logger.info(RETURN_TEMPLATE, toJson(logData));
    }

    public static void error(String traceId, String api, Object params, Throwable e) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("traceId", traceId);
        logData.put("api", api);
        logData.put("params", toJson(params));
        logData.put("error", e.getMessage());
        logData.put("type", "error");
        logger.error(ERROR_TEMPLATE, toJson(logData), e);
    }

    public static void error(String traceId, String api, String message) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("traceId", traceId);
        logData.put("api", api);
        logData.put("error", message);
        logData.put("type", "error");
        logger.error(ERROR_TEMPLATE, toJson(logData));
    }

    public static void warn(String traceId, String api, String message) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("traceId", traceId);
        logData.put("api", api);
        logData.put("message", message);
        logData.put("type", "warn");
        logger.warn(WARN_TEMPLATE, toJson(logData));
    }

    public static void sql(String traceId, String sql, Object params, Object result) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("traceId", traceId);
        logData.put("sql", sql);
        logData.put("params", toJson(params));
        logData.put("result", toJson(result));
        logData.put("type", "sql");
        logger.debug(SQL_TEMPLATE, toJson(logData));
    }

    public static void info(String traceId, String type, String message) {
        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("traceId", traceId);
        logData.put("type", type);
        logData.put("message", message);
        logger.info("[{}] {}", type, toJson(logData));
    }
}