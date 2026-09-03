package com.example.spring_vue_demo.service.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.spring_vue_demo.entity.WorkOrder;
import com.example.spring_vue_demo.enums.ErrorCode;
import com.example.spring_vue_demo.exception.UserSideException;
import com.example.spring_vue_demo.param.WorkOrder.WorkOrderPageParam;
import io.micrometer.common.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * @author wtt
 * @date 2025/05/31
 */
public class WorkOrderQuery {

    public static LambdaQueryWrapper<WorkOrder> getPageWorkWrapper(WorkOrderPageParam param, List<Long> orderIds) {
        LambdaQueryWrapper<WorkOrder> pageWrapper=new LambdaQueryWrapper<WorkOrder>()
                .eq(Objects.nonNull(param.getId()),WorkOrder::getId,param.getId())
                .in(CollectionUtils.isNotEmpty(orderIds),WorkOrder::getId,orderIds)
                .eq(StringUtils.isNotBlank(param.getCode()),WorkOrder::getCode,param.getCode())
                .eq(Objects.nonNull(param.getType()),WorkOrder::getType,param.getType())
                .eq(StringUtils.isNotBlank(param.getTitle()),WorkOrder::getTitle,param.getTitle())
                .eq(Objects.nonNull(param.getPriorityLevel()),WorkOrder::getPriorityLevel,param.getPriorityLevel())
                .in(CollectionUtils.isNotEmpty(param.getStatus()),WorkOrder::getStatus,param.getStatus())
                .ge(Objects.nonNull(param.getCreateTimeFrom()),WorkOrder::getCreateTime,param.getCreateTimeFrom())
                .le(Objects.nonNull(param.getCreateTimeTo()),WorkOrder::getCreateTime,param.getCreateTimeTo())
                .ge(Objects.nonNull(param.getDeadLineFrom()),WorkOrder::getDeadlineTime,param.getDeadLineFrom())
                .le(Objects.nonNull(param.getDeadLineTo()),WorkOrder::getDeadlineTime,param.getDeadLineTo())
                .orderBy(true,false,WorkOrder::getCreateTime)
                ;
        return pageWrapper;
    }

    public static Page<WorkOrder> getPageInfoWrapper(WorkOrderPageParam param){
        Page<WorkOrder> pageWrapper=new Page<WorkOrder>();
        pageWrapper.setCurrent(param.getPageNum());
        pageWrapper.setSize(param.getPageSize());
        return pageWrapper;
    }

    public static LambdaQueryWrapper<WorkOrder> getWorkOrderWrapper(Long id,String code) {
        if(id==null&&code==null){
            throw new UserSideException(ErrorCode.ID_AND_CODE_IS_NULL);
        }
        LambdaQueryWrapper<WorkOrder>wrapper=new LambdaQueryWrapper<WorkOrder>()
                .eq(Objects.nonNull(id),WorkOrder::getId,id)
                .eq(StringUtils.isNotBlank(code),WorkOrder::getCode,code);
        return wrapper;
    }


    public static LambdaQueryWrapper<WorkOrder> getByDateStatus(List<Integer> statusList, LocalDateTime createTimeTo, LocalDateTime createTimeFrom) {
        LambdaQueryWrapper<WorkOrder>wrapper=new LambdaQueryWrapper<WorkOrder>()
                .in(CollectionUtils.isNotEmpty(statusList),WorkOrder::getStatus,statusList)
                .le(Objects.nonNull(createTimeTo),WorkOrder::getCreateTime,createTimeTo)
                .ge(Objects.nonNull(createTimeFrom),WorkOrder::getCreateTime,createTimeFrom);
        return wrapper;
    }

    public static LambdaQueryWrapper<WorkOrder> getWorkOrderByIds(List<Long> orderIds) {
        LambdaQueryWrapper<WorkOrder>wrapper=new LambdaQueryWrapper<>();
        if(CollectionUtils.isEmpty(orderIds)){
            wrapper.apply("1=2");
            return wrapper;
        }
        wrapper.in(true,WorkOrder::getId,orderIds).orderBy(true,false,WorkOrder::getCreateTime);
        return wrapper;
    }

    public static QueryWrapper<WorkOrder> getCountGroupByStatusByDate(LocalDateTime createTimeFrom, LocalDateTime createTimeTo) {
        QueryWrapper<WorkOrder>wrapper=new QueryWrapper<WorkOrder>()
                .select("status","COUNT(*) as count")
                .ge(Objects.nonNull(createTimeFrom),"create_time", createTimeFrom)
                .le(Objects.nonNull(createTimeTo),"create_time",createTimeTo)
                .groupBy("status");
        return wrapper;
    }

    public static QueryWrapper<WorkOrder> getCountGroupByTypeByDate(LocalDateTime createTimeFrom, LocalDateTime createTimeTo) {
        QueryWrapper<WorkOrder>wrapper=new QueryWrapper<WorkOrder>()
                .select("type","COUNT(*) as count")
                .ge(Objects.nonNull(createTimeFrom),"create_time", createTimeFrom)
                .le(Objects.nonNull(createTimeTo),"create_time",createTimeTo)
                .groupBy("type");
        return wrapper;
    }

    public static LambdaUpdateWrapper<WorkOrder> getUpdateStatusByIdWrapper(Long orderId, Integer status) {
        LambdaUpdateWrapper<WorkOrder>wrapper=new LambdaUpdateWrapper<WorkOrder>()
                .set(true,WorkOrder::getStatus,status)
                .eq(true,WorkOrder::getId,orderId);
        return wrapper;
    }

}
