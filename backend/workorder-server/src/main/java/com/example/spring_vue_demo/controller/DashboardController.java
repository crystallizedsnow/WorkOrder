package com.example.spring_vue_demo.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.spring_vue_demo.param.MessageParam;
import com.example.spring_vue_demo.param.StatusDataParam;
import com.example.spring_vue_demo.param.WeekHandleQuantityParam;
import com.example.spring_vue_demo.service.DashboardService;
import com.example.spring_vue_demo.utils.LogUtils;
import com.example.spring_vue_demo.vo.*;
import com.example.spring_vue_demo.vo.WorkOrder.WorkOrderDataVO;
import com.example.spring_vue_demo.vo.WorkOrder.WorkOrderTodoVO;
import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author wtt
 * @date 2025/05/25
 */
@RestController
@RequiredArgsConstructor
@Tag(name="工作台")
@RequestMapping("/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;

    @ApiOperationSupport(order = 1)
    @Operation(summary = "数据看板")
    @PostMapping("/data")
    public WorkOrderDataVO getData(){
        String traceId = MDC.get("traceId");
        LogUtils.entrance(traceId, "/dashboard/data", "POST", null);
        try {
            WorkOrderDataVO result = dashboardService.getData();
            LogUtils.returnLog(traceId, "/dashboard/data", "POST", result);
            return result;
        } catch (Exception e) {
            LogUtils.error(traceId, "/dashboard/data", null, e);
            throw e;
        }
    }

    @ApiOperationSupport(order = 2)
    @Operation(summary = "待办事项")
    @PostMapping("/todo")
    public List<WorkOrderTodoVO> getTodo(){
        String traceId = MDC.get("traceId");
        LogUtils.entrance(traceId, "/dashboard/todo", "POST", null);
        try {
            List<WorkOrderTodoVO> result = dashboardService.getTodo();
            LogUtils.returnLog(traceId, "/dashboard/todo", "POST", result);
            return result;
        } catch (Exception e) {
            LogUtils.error(traceId, "/dashboard/todo", null, e);
            throw e;
        }
    }

    @ApiOperationSupport(order = 3)
    @Operation(summary = "工单状态统计")
    @PostMapping("/status")
    public List<StatusDataVO> countStatus(@RequestBody StatusDataParam param){
        String traceId = MDC.get("traceId");
        LogUtils.entrance(traceId, "/dashboard/status", "POST", param);
        try {
            List<StatusDataVO> result = dashboardService.getStatus(param);
            LogUtils.returnLog(traceId, "/dashboard/status", "POST", result);
            return result;
        } catch (Exception e) {
            LogUtils.error(traceId, "/dashboard/status", param, e);
            throw e;
        }
    }

    @ApiOperationSupport(order = 4)
    @Operation(summary = "本周处理数量")
    @PostMapping("/handleQuantity")
    public List<WeekHandleVO> getHandleQuantity(){
        String traceId = MDC.get("traceId");
        LogUtils.entrance(traceId, "/dashboard/handleQuantity", "POST", null);
        try {
            List<WeekHandleVO> result = dashboardService.getWeekHandleQuantity();
            LogUtils.returnLog(traceId, "/dashboard/handleQuantity", "POST", result);
            return result;
        } catch (Exception e) {
            LogUtils.error(traceId, "/dashboard/handleQuantity", null, e);
            throw e;
        }
    }

    @ApiOperationSupport(order = 5)
    @Operation(summary = "消息中心")
    @PostMapping("/pageMessages")
    public IPage<MessageVO> pageMessages(@RequestBody MessageParam param){
        String traceId = MDC.get("traceId");
        LogUtils.entrance(traceId, "/dashboard/pageMessages", "POST", param);
        try {
            IPage<MessageVO> result = dashboardService.pageMessages(param);
            LogUtils.returnLog(traceId, "/dashboard/pageMessages", "POST", result);
            return result;
        } catch (Exception e) {
            LogUtils.error(traceId, "/dashboard/pageMessages", param, e);
            throw e;
        }
    }

    @ApiOperationSupport(order = 6)
    @Operation(summary = "工单类型统计")
    @PostMapping("/type")
    public List<TypeDataVO> countType(@RequestBody StatusDataParam param){
        String traceId = MDC.get("traceId");
        LogUtils.entrance(traceId, "/dashboard/type", "POST", param);
        try {
            List<TypeDataVO> result = dashboardService.getType(param);
            LogUtils.returnLog(traceId, "/dashboard/type", "POST", result);
            return result;
        } catch (Exception e) {
            LogUtils.error(traceId, "/dashboard/type", param, e);
            throw e;
        }
    }
}
