package com.example.workorder.cli.feign;

import com.example.workorder.api.param.Flow.FlowIdParam;
import com.example.workorder.api.param.Flow.FlowPageParam;
import com.example.workorder.cli.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "workorder-backend", contextId = "flow", url = "${workorder.backend.url}", configuration = FeignConfig.class)
public interface FlowFeignClient {

    @PostMapping("/flow/getById")
    ResponseEntity<?> getById(
            @RequestHeader("Authorization") String token,
            @RequestHeader("X-Trace-ID") String traceId,
            @RequestBody FlowIdParam param);

    @PostMapping("/flow/page")
    ResponseEntity<?> page(
            @RequestHeader("Authorization") String token,
            @RequestHeader("X-Trace-ID") String traceId,
            @RequestBody FlowPageParam param);

    @PostMapping("/flow/create")
    ResponseEntity<?> createFlow(
            @RequestHeader("Authorization") String token,
            @RequestHeader("X-Trace-ID") String traceId,
            @RequestBody Map<String, Object> param);

    @PostMapping("/flow/edit")
    ResponseEntity<?> editFlow(
            @RequestHeader("Authorization") String token,
            @RequestHeader("X-Trace-ID") String traceId,
            @RequestBody Map<String, Object> param);

    @PostMapping("/flow/delete")
    ResponseEntity<?> deleteFlow(
            @RequestHeader("Authorization") String token,
            @RequestHeader("X-Trace-ID") String traceId,
            @RequestBody Map<String, Object> param);
}