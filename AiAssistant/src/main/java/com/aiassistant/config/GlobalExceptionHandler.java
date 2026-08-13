package com.aiassistant.config;

import com.aiassistant.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException e) {
        log.warn("Request rejected: status={} reason={}", e.getStatusCode().value(), e.getReason());
        Map<String, Object> response = new HashMap<>();
        response.put("code", e.getStatusCode().value());
        response.put("message", e.getReason() == null ? "请求被拒绝" : e.getReason());
        response.put("data", null);
        response.put("traceId", generateTraceId());
        return ResponseEntity.status(e.getStatusCode()).contentType(MediaType.APPLICATION_JSON).body(response);
    }

    @ExceptionHandler(ServiceException.class)
    public Flux<String> handleServiceException(ServiceException e) {
        log.error("ServiceException: {}", e.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", e.getCode());
        response.put("message", e.getMessage());
        response.put("data", null);
        response.put("traceId", generateTraceId());
        
        try {
            return Flux.just("data: " + objectMapper.writeValueAsString(response) + "\n\n");
        } catch (Exception ex) {
            return Flux.just("data: {\"code\":" + e.getCode() + ",\"message\":\"" + e.getMessage() + "\"}\n\n");
        }
    }

    @ExceptionHandler(WebClientException.class)
    public Flux<String> handleWebClientException(WebClientException e) {
        log.error("WebClientException: {}", e.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", 503);
        response.put("message", "服务暂时不可用，请稍后重试");
        response.put("data", null);
        response.put("traceId", generateTraceId());
        
        try {
            return Flux.just("data: " + objectMapper.writeValueAsString(response) + "\n\n");
        } catch (Exception ex) {
            return Flux.just("data: {\"code\":503,\"message\":\"服务暂时不可用，请稍后重试\"}\n\n");
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Flux<String> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("IllegalArgumentException: {}", e.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", 400);
        response.put("message", e.getMessage());
        response.put("data", null);
        response.put("traceId", generateTraceId());
        
        try {
            return Flux.just("data: " + objectMapper.writeValueAsString(response) + "\n\n");
        } catch (Exception ex) {
            return Flux.just("data: {\"code\":400,\"message\":\"" + e.getMessage() + "\"}\n\n");
        }
    }

    @ExceptionHandler(HttpMessageNotWritableException.class)
    public Flux<String> handleHttpMessageNotWritableException(HttpMessageNotWritableException e) {
        log.error("HttpMessageNotWritableException: {}", e.getMessage(), e);
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", 500);
        response.put("message", "响应格式转换错误");
        response.put("data", null);
        response.put("traceId", generateTraceId());
        
        try {
            return Flux.just("data: " + objectMapper.writeValueAsString(response) + "\n\n");
        } catch (Exception ex) {
            return Flux.just("data: {\"code\":500,\"message\":\"响应格式转换错误\"}\n\n");
        }
    }

    @ExceptionHandler(RuntimeException.class)
    public Flux<String> handleRuntimeException(RuntimeException e) {
        log.error("RuntimeException: {}", e.getMessage(), e);
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", 500);
        response.put("message", "系统内部错误，请联系管理员");
        response.put("data", null);
        response.put("traceId", generateTraceId());
        
        try {
            return Flux.just("data: " + objectMapper.writeValueAsString(response) + "\n\n");
        } catch (Exception ex) {
            return Flux.just("data: {\"code\":500,\"message\":\"系统内部错误，请联系管理员\"}\n\n");
        }
    }

    @ExceptionHandler(Exception.class)
    public Flux<String> handleException(Exception e) {
        log.error("Exception: {}", e.getMessage(), e);
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", 500);
        response.put("message", "服务暂时不可用，请稍后重试");
        response.put("data", null);
        response.put("traceId", generateTraceId());
        
        try {
            return Flux.just("data: " + objectMapper.writeValueAsString(response) + "\n\n");
        } catch (Exception ex) {
            return Flux.just("data: {\"code\":500,\"message\":\"服务暂时不可用，请稍后重试\"}\n\n");
        }
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
