package com.example.spring_vue_demo.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.spring_vue_demo.entity.Result;
import com.example.spring_vue_demo.entity.SearchResult;
import com.example.spring_vue_demo.entity.WorkOrder;
import com.example.spring_vue_demo.param.WorkOrder.*;
import com.example.spring_vue_demo.param.WorkOrder.WorkOrderPageParam;
import com.example.spring_vue_demo.service.WorkOrderService;
import com.example.spring_vue_demo.utils.LogUtils;
import com.example.spring_vue_demo.vo.WorkOrder.WorkOrderDetailVO;
import com.example.spring_vue_demo.vo.WorkOrder.WorkOrderPageVO;
import com.example.spring_vue_demo.vo.WorkOrder.WorkOrderUpdateStatusVO;
import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


/**
 * @author wtt
 * @date 2025/05/24
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "工单管理")
@RequestMapping("/workOrder")
public class WorkOrderController {
    @Autowired
    private WorkOrderService workOrderService;

    @ApiOperationSupport(order = 1)
    @Operation(summary = "分页")
    @PostMapping("/page")
    public IPage<WorkOrderPageVO> page(@RequestBody WorkOrderPageParam param) {
        String traceId = MDC.get("traceId");
        LogUtils.entrance(traceId, "/workOrder/page", "POST", param);
        try {
            IPage<WorkOrderPageVO> result = workOrderService.pageWorkOrder(param);
            LogUtils.returnLog(traceId, "/workOrder/page", "POST", result);
            return result;
        } catch (Exception e) {
            LogUtils.error(traceId, "/workOrder/page", param, e);
            throw e;
        }
    }

    @ApiOperationSupport(order = 2)
    @Operation(summary = "详情")
    @PostMapping("/detail")
    public WorkOrderDetailVO detail(@RequestBody WorkOrderDetailParam param) {
        String traceId = MDC.get("traceId");
        LogUtils.entrance(traceId, "/workOrder/detail", "POST", param);
        try {
            WorkOrderDetailVO result = workOrderService.detail(param);
            LogUtils.returnLog(traceId, "/workOrder/detail", "POST", result);
            return result;
        } catch (Exception e) {
            LogUtils.error(traceId, "/workOrder/detail", param, e);
            throw e;
        }
    }

    @ApiOperationSupport(order = 3)
    @Operation(summary = "工单操作")
    @PostMapping("/handle")
    public WorkOrderUpdateStatusVO updateWorkOrderStatus(@RequestBody WorkOrderHandleParam param) {
        String traceId = MDC.get("traceId");
        LogUtils.entrance(traceId, "/workOrder/handle", "POST", param);
        try {
            WorkOrderUpdateStatusVO result = workOrderService.handleWorkOrder(param);
            LogUtils.returnLog(traceId, "/workOrder/handle", "POST", result);
            return result;
        } catch (Exception e) {
            LogUtils.error(traceId, "/workOrder/handle", param, e);
            throw e;
        }
    }

    @ApiOperationSupport(order = 4)
    @Operation(summary = "删除")
    @PostMapping("/delete")
    public WorkOrderUpdateStatusVO delete(@RequestBody WorkOrderDeleteParam param) {
        String traceId = MDC.get("traceId");
        LogUtils.entrance(traceId, "/workOrder/delete", "POST", param);
        try {
            WorkOrderUpdateStatusVO result = workOrderService.deleteOrder(param);
            LogUtils.returnLog(traceId, "/workOrder/delete", "POST", result);
            return result;
        } catch (Exception e) {
            LogUtils.error(traceId, "/workOrder/delete", param, e);
            throw e;
        }
    }

    @ApiOperationSupport(order = 5)
    @Operation(summary = "取消")
    @PostMapping("/cancel")
    public WorkOrderUpdateStatusVO cancel(@RequestBody WorkOrderCancelParam param) {
        String traceId = MDC.get("traceId");
        LogUtils.entrance(traceId, "/workOrder/cancel", "POST", param);
        try {
            WorkOrderUpdateStatusVO result = workOrderService.cancel(param);
            LogUtils.returnLog(traceId, "/workOrder/cancel", "POST", result);
            return result;
        } catch (Exception e) {
            LogUtils.error(traceId, "/workOrder/cancel", param, e);
            throw e;
        }
    }


    @ApiOperationSupport(order = 6)
    @Operation(summary = "新建工单")
    @PostMapping("/create")
    public Result create(@RequestBody WorkOrderCreateParam param) {
        String traceId = MDC.get("traceId");
        LogUtils.entrance(traceId, "/workOrder/create", "POST", param);
        try {
            Result result = workOrderService.create(param);
            LogUtils.returnLog(traceId, "/workOrder/create", "POST", result);
            return result;
        } catch (Exception e) {
            LogUtils.error(traceId, "/workOrder/create", param, e);
            throw e;
        }
    }

    @ApiOperationSupport(order = 7)
    @Operation(summary = "审批")
    @PostMapping("/approval")
    public Result approval(@RequestBody WorkOrderApprovalParam param) {
        String traceId = MDC.get("traceId");
        LogUtils.entrance(traceId, "/workOrder/approval", "POST", param);
        try {
            Result result = workOrderService.approval(param);
            LogUtils.returnLog(traceId, "/workOrder/approval", "POST", result);
            return result;
        } catch (Exception e) {
            LogUtils.error(traceId, "/workOrder/approval", param, e);
            throw e;
        }
    }

    @ApiOperationSupport(order = 8)
    @Operation(summary = "批量导出")
    @PostMapping("/export")
    public void export(@RequestBody WorkOrderPageParam param, HttpServletResponse response){
        String traceId = MDC.get("traceId");
        LogUtils.entrance(traceId, "/workOrder/export", "POST", param);
        try {
            workOrderService.export(param,response);
            LogUtils.returnLog(traceId, "/workOrder/export", "POST", "success");
        } catch (Exception e) {
            LogUtils.error(traceId, "/workOrder/export", param, e);
            throw e;
        }
    }

    @ApiOperationSupport(order = 9)
    @Operation(summary = "打印工单")
    @PostMapping("/print")
    public void print(@RequestBody WorkOrderDetailParam param, HttpServletResponse response){
        String traceId = MDC.get("traceId");
        LogUtils.entrance(traceId, "/workOrder/print", "POST", param);
        try {
            workOrderService.print(param,response);
            LogUtils.returnLog(traceId, "/workOrder/print", "POST", "success");
        } catch (Exception e) {
            LogUtils.error(traceId, "/workOrder/print", param, e);
            throw e;
        }
    }

    @ApiOperationSupport(order = 10)
    @Operation(summary = "根据关键词搜索工单信息")
    @GetMapping("/search")
    public SearchResult<WorkOrder> search(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) throws IOException {
        String traceId = MDC.get("traceId");
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", keyword);
        params.put("pageNum", pageNum);
        params.put("pageSize", pageSize);
        LogUtils.entrance(traceId, "/workOrder/search", "GET", params);
        try {
            SearchResult<WorkOrder> result = workOrderService.searchWorkOrders(keyword, pageNum, pageSize);
            LogUtils.returnLog(traceId, "/workOrder/search", "GET", result);
            return result;
        } catch (Exception e) {
            LogUtils.error(traceId, "/workOrder/search", params, e);
            throw e;
        }
    }

}
