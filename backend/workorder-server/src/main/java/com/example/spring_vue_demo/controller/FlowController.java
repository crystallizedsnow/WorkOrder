package com.example.spring_vue_demo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.spring_vue_demo.param.Flow.FlowCreateParam;
import com.example.spring_vue_demo.param.Flow.FlowIdParam;
import com.example.spring_vue_demo.param.Flow.FlowPageParam;
import com.example.spring_vue_demo.param.Flow.FlowUpdateParam;
import com.example.spring_vue_demo.service.FlowService;
import com.example.spring_vue_demo.utils.LogUtils;
import com.example.spring_vue_demo.vo.Flow.FlowCreateVO;
import com.example.spring_vue_demo.vo.Flow.FlowVO;
import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;


/**
 * @author wtt
 * @date 2025/06/14
 */
@RestController
@RequestMapping("/flow")
@AllArgsConstructor
@Tag(name = "FlowController", description = "工单流程管理接口")
public class FlowController {
    private final FlowService flowService;

    @ApiOperationSupport(order = 1)
    @PostMapping("/create")
    @Operation(summary = "新增流程")
    public FlowCreateVO create(@RequestBody FlowCreateParam param) {
        String traceId = MDC.get("traceId");
        LogUtils.entrance(traceId, "/flow/create", "POST", param);
        try {
            FlowCreateVO result = flowService.create(param, null);
            LogUtils.returnLog(traceId, "/flow/create", "POST", result);
            return result;
        } catch (Exception e) {
            LogUtils.error(traceId, "/flow/create", param, e);
            throw e;
        }
    }

    @ApiOperationSupport(order = 2)
    @PostMapping("/edit")
    @Operation(summary = "编辑流程")
    public FlowCreateVO edit(@RequestBody FlowUpdateParam param) {
        String traceId = MDC.get("traceId");
        LogUtils.entrance(traceId, "/flow/edit", "POST", param);
        try {
            FlowCreateVO result = flowService.update(param);
            LogUtils.returnLog(traceId, "/flow/edit", "POST", result);
            return result;
        } catch (Exception e) {
            LogUtils.error(traceId, "/flow/edit", param, e);
            throw e;
        }
    }

    @ApiOperationSupport(order = 3)
    @PostMapping("/delete")
    @Operation(summary = "删除流程")
    public boolean delete(@RequestBody FlowIdParam param){
        String traceId = MDC.get("traceId");
        LogUtils.entrance(traceId, "/flow/delete", "POST", param);
        try {
            boolean result = flowService.delete(param);
            LogUtils.returnLog(traceId, "/flow/delete", "POST", result);
            return result;
        } catch (Exception e) {
            LogUtils.error(traceId, "/flow/delete", param, e);
            throw e;
        }
    }

    @ApiOperationSupport(order = 4)
    @PostMapping("/getById")
    @Operation(summary = "根据流程ID查询")
    public FlowVO getByFlowId(@RequestBody FlowIdParam param) {
        String traceId = MDC.get("traceId");
        LogUtils.entrance(traceId, "/flow/getById", "POST", param);
        try {
            FlowVO result = flowService.getByFlowId(param);
            LogUtils.returnLog(traceId, "/flow/getById", "POST", result);
            return result;
        } catch (Exception e) {
            LogUtils.error(traceId, "/flow/getById", param, e);
            throw e;
        }
    }

    @ApiOperationSupport(order = 5)
    @PostMapping("/page")
    @Operation(summary = "分页查询")
    public Page<FlowVO> pageByFlowId(@RequestBody FlowPageParam param){
        String traceId = MDC.get("traceId");
        LogUtils.entrance(traceId, "/flow/page", "POST", param);
        try {
            Page<FlowVO> result = flowService.page(param);
            LogUtils.returnLog(traceId, "/flow/page", "POST", result);
            return result;
        } catch (Exception e) {
            LogUtils.error(traceId, "/flow/page", param, e);
            throw e;
        }
    }
}
