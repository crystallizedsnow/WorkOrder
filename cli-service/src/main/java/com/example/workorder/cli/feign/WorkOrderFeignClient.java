package com.example.workorder.cli.feign;

import com.example.workorder.api.param.WorkOrder.WorkOrderDetailParam;
import com.example.workorder.api.param.WorkOrder.WorkOrderPageParam;
import com.example.workorder.cli.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "workorder-backend", contextId = "workOrder", url = "${workorder.backend.url}", configuration = FeignConfig.class)
public interface WorkOrderFeignClient {

    @PostMapping("/workOrder/page")
    ResponseEntity<?> pageWorkOrder(
            @RequestHeader("Authorization") String token,
            @RequestHeader("X-Trace-ID") String traceId,
            @RequestBody WorkOrderPageParam param);

    @PostMapping("/workOrder/detail")
    ResponseEntity<?> detail(
            @RequestHeader("Authorization") String token,
            @RequestHeader("X-Trace-ID") String traceId,
            @RequestBody WorkOrderDetailParam param);

    @GetMapping("/workOrder/search")
    ResponseEntity<?> searchWorkOrders(
            @RequestHeader("Authorization") String token,
            @RequestHeader("X-Trace-ID") String traceId,
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize);

    @PostMapping("/workOrder/create")
    ResponseEntity<?> createWorkOrder(
            @RequestHeader("Authorization") String token,
            @RequestHeader("X-Trace-ID") String traceId,
            @RequestBody Map<String, Object> param);

    @PostMapping("/workOrder/handle")
    ResponseEntity<?> handleWorkOrder(
            @RequestHeader("Authorization") String token,
            @RequestHeader("X-Trace-ID") String traceId,
            @RequestBody Map<String, Object> param);

    @PostMapping("/workOrder/delete")
    ResponseEntity<?> deleteWorkOrder(
            @RequestHeader("Authorization") String token,
            @RequestHeader("X-Trace-ID") String traceId,
            @RequestBody Map<String, Object> param);

    @PostMapping("/workOrder/cancel")
    ResponseEntity<?> cancelWorkOrder(
            @RequestHeader("Authorization") String token,
            @RequestHeader("X-Trace-ID") String traceId,
            @RequestBody Map<String, Object> param);

    @PostMapping("/workOrder/approval")
    ResponseEntity<?> approvalWorkOrder(
            @RequestHeader("Authorization") String token,
            @RequestHeader("X-Trace-ID") String traceId,
            @RequestBody Map<String, Object> param);
}