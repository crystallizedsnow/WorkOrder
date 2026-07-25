package com.example.spring_vue_demo.service.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.example.spring_vue_demo.entity.HandleUserInfo;
import com.example.spring_vue_demo.entity.WorkOrder;
import com.example.spring_vue_demo.enums.HandleUserInfoHandleTypeEnum;
import com.example.spring_vue_demo.param.HandleUserInfoParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * @author wtt
 * @date 2025/05/31
 */
public class HandleUserInfoQuery {
    public static LambdaQueryWrapper<HandleUserInfo> getWorkOrderPageWrapper(HandleUserInfoParam handleUserInfo, Integer type) {
        LambdaQueryWrapper<HandleUserInfo> wrapper = new LambdaQueryWrapper<HandleUserInfo>()
                .eq(true, HandleUserInfo::getHandleType, type)
                .eq(handleUserInfo.getUserId()!=null, HandleUserInfo::getUserId, handleUserInfo.getUserId() )
                .eq( StringUtils.isNotBlank(handleUserInfo.getDepartmentCode()), HandleUserInfo::getDepartmentCode, handleUserInfo.getDepartmentCode())
                .eq(StringUtils.isNotBlank(handleUserInfo.getCompanyCode()), HandleUserInfo::getCompanyCode, handleUserInfo.getCompanyCode())
                .eq(handleUserInfo.getFinished()!=null,HandleUserInfo::getFinished,handleUserInfo.getFinished());
        return wrapper;
    }

    public static LambdaQueryWrapper<HandleUserInfo> getPageHandleUserInfoWrapper(List<Long> orderIds) {
        LambdaQueryWrapper<HandleUserInfo> wrapper = new LambdaQueryWrapper<HandleUserInfo>();
        if (CollectionUtils.isEmpty(orderIds)) {
            // 添加一个不可能满足的条件
            wrapper.apply("1=0");
            return wrapper;
        }
        wrapper.in(HandleUserInfo::getOrderId, orderIds);
        return wrapper;
    }

    public static LambdaQueryWrapper<HandleUserInfo> getOrderIdWrapper(Long orderId) {
        LambdaQueryWrapper<HandleUserInfo> wrapper = new LambdaQueryWrapper<HandleUserInfo>()
                .eq(true, HandleUserInfo::getOrderId, orderId);
        return wrapper;
    }

    public static LambdaUpdateWrapper<HandleUserInfo> getUpdateStatusWrapper(Long orderId, List<Long> staffIds, Integer handleType
            , Boolean finished, LocalDateTime handleTime, String remark) {
        LambdaUpdateWrapper<HandleUserInfo> wrapper = new LambdaUpdateWrapper<HandleUserInfo>();
        if (CollectionUtils.isEmpty(staffIds)) {
            wrapper.apply("1=0");
            return wrapper;
        }
        wrapper.eq(true, HandleUserInfo::getOrderId, orderId)
                .in(true, HandleUserInfo::getUserId, staffIds)
                .eq(true, HandleUserInfo::getHandleType, handleType)
                .set(HandleUserInfo::getHandleTime, handleTime)
                .set(HandleUserInfo::getFinished, finished)
                .set(HandleUserInfo::getRemark, remark);
        return wrapper;
    }

    public static LambdaQueryWrapper<HandleUserInfo> getHandleTypeWrapper(Long orderId, Integer handleType) {
        LambdaQueryWrapper<HandleUserInfo> wrapper = new LambdaQueryWrapper<HandleUserInfo>()
                .eq(true, HandleUserInfo::getOrderId, orderId)
                .eq(true, HandleUserInfo::getHandleType, handleType);
        return wrapper;
    }

    public static LambdaQueryWrapper<HandleUserInfo> getHandleTypeUserIdWrapper(Long orderId, Integer handleType, Long staffId) {
        LambdaQueryWrapper<HandleUserInfo> wrapper = new LambdaQueryWrapper<HandleUserInfo>()
                .eq(true, HandleUserInfo::getOrderId, orderId)
                .eq(true, HandleUserInfo::getHandleType, handleType)
                .eq(true, HandleUserInfo::getUserId, staffId);
        return wrapper;
    }


    public static LambdaQueryWrapper<HandleUserInfo> getOrderIdByUserIdHandleTypeWrapper(Long userId, List<Integer> handleType, Boolean finished) {
        LambdaQueryWrapper<HandleUserInfo> wrapper = new LambdaQueryWrapper<HandleUserInfo>()
                .eq(true, HandleUserInfo::getUserId, userId)
                .in(true, HandleUserInfo::getHandleType, handleType)
                .eq(true, HandleUserInfo::getFinished, finished);
        return wrapper;
    }


    public static LambdaQueryWrapper<WorkOrder> getByStatusAndDeadlineTime(LocalDateTime nowTime, List<Integer> status) {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<WorkOrder>()
                .le(Objects.nonNull(nowTime), WorkOrder::getDeadlineTime, nowTime)
                .in(true, WorkOrder::getStatus, status);
        return wrapper;
    }

    public static LambdaQueryWrapper<HandleUserInfo> getByHandleDate(LocalDateTime weekAgo, LocalDateTime today) {
        LambdaQueryWrapper<HandleUserInfo> wrapper = new LambdaQueryWrapper<HandleUserInfo>()
                .ge(Objects.nonNull(weekAgo),HandleUserInfo::getCreateTime,weekAgo)
                .le(Objects.nonNull(today),HandleUserInfo::getCreateTime,today)
                .or()
                .le(Objects.nonNull(today),HandleUserInfo::getHandleTime,today)
                .eq(HandleUserInfo::getHandleType,HandleUserInfoHandleTypeEnum.HANDLE.getValue());
        return wrapper;
    }
}

