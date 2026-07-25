package com.example.spring_vue_demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.spring_vue_demo.entity.HandleUserInfo;
import com.example.spring_vue_demo.entity.Message;
import com.example.spring_vue_demo.entity.WorkOrder;
import com.example.spring_vue_demo.enums.HandleUserInfoHandleTypeEnum;
import com.example.spring_vue_demo.enums.TimeTypeEnum;
import com.example.spring_vue_demo.enums.WorkOrderStatusEnum;
import com.example.spring_vue_demo.enums.WorkOrderTypeEnum;
import com.example.spring_vue_demo.mapper.HandleUserInfoMapper;
import com.example.spring_vue_demo.mapper.MessageMapper;
import com.example.spring_vue_demo.mapper.WorkOrderMapper;
import com.example.spring_vue_demo.param.MessageParam;
import com.example.spring_vue_demo.param.StatusDataParam;
import com.example.spring_vue_demo.param.WeekHandleQuantityParam;
import com.example.spring_vue_demo.service.DashboardService;
import com.example.spring_vue_demo.service.convert.MessageConverter;
import com.example.spring_vue_demo.service.convert.WorkOrderConverter;
import com.example.spring_vue_demo.service.query.HandleUserInfoQuery;
import com.example.spring_vue_demo.service.query.MessageQuery;
import com.example.spring_vue_demo.service.query.WorkOrderQuery;
import com.example.spring_vue_demo.utils.StaffHolder;
import com.example.spring_vue_demo.vo.*;
import com.example.spring_vue_demo.vo.WorkOrder.WorkOrderDataVO;
import com.example.spring_vue_demo.vo.WorkOrder.WorkOrderTodoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author wtt
 * @date 2025/05/25
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceimpl implements DashboardService {
    private final WorkOrderMapper workOrderMapper;
    private final HandleUserInfoMapper handleUserInfoMapper;
    private final MessageMapper messageMapper;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final DateTimeFormatter localDateTimeformatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public WorkOrderDataVO getData() {
        LocalDateTime currentTime = LocalDateTime.now();
        LocalDateTime monthAgoTime = LocalDateTime.now().minusMonths(1L);
        LambdaQueryWrapper<WorkOrder> finishWrapper = WorkOrderQuery.getByDateStatus(List.of(WorkOrderStatusEnum.FINISHED.getValue(), WorkOrderStatusEnum.CHECKED.getValue()), currentTime, monthAgoTime);
        Long monthFinishedNum = workOrderMapper.selectCount(finishWrapper);
        LambdaQueryWrapper<WorkOrder> unHandledWrapper = WorkOrderQuery.getByDateStatus(List.of(WorkOrderStatusEnum.HANDLING.getValue(), WorkOrderStatusEnum.DELAYED.getValue()), null, null);
        Long unHandledNum = workOrderMapper.selectCount(unHandledWrapper);
        LambdaQueryWrapper<WorkOrder> unAuditedWrapper = WorkOrderQuery.getByDateStatus(List.of(WorkOrderStatusEnum.UNAUDITED.getValue()), null, null);
        Long unAuditedNum = workOrderMapper.selectCount(unAuditedWrapper);
        LambdaQueryWrapper<WorkOrder> delayWrapper = WorkOrderQuery.getByDateStatus(List.of(WorkOrderStatusEnum.DELAYED.getValue()), null, null);
        Long delayNum = workOrderMapper.selectCount(delayWrapper);
        WorkOrderDataVO vo = new WorkOrderDataVO();
        vo.setMonthFinishedNum(monthFinishedNum);
        vo.setUnHandledNum(unHandledNum);
        vo.setUnAuditedNum(unAuditedNum);
        vo.setDelayNum(delayNum);
        return vo;
    }

    @Override
    public List<WorkOrderTodoVO> getTodo() {
        //查询该用户应审核、应分配、应完成、应确认完成的工单
        Long userId = StaffHolder.get().getId();
        //查询该用户对应的用户信息
        LambdaQueryWrapper<HandleUserInfo> handleUserInfowrapper = HandleUserInfoQuery.getOrderIdByUserIdHandleTypeWrapper(userId, List.of(HandleUserInfoHandleTypeEnum.AUDIT.getValue(), HandleUserInfoHandleTypeEnum.DISTRIBUTE.getValue(),
                HandleUserInfoHandleTypeEnum.HANDLE.getValue(), HandleUserInfoHandleTypeEnum.CHECK.getValue()), Boolean.FALSE);
        List<HandleUserInfo> handleUserInfos = handleUserInfoMapper.selectList(handleUserInfowrapper);
        List<Long> orderIds = handleUserInfos.stream().map(HandleUserInfo::getOrderId).distinct().toList();
        LambdaQueryWrapper<WorkOrder> workOrderWrapper = WorkOrderQuery.getWorkOrderByIds(orderIds);
        List<WorkOrder> workOrders = workOrderMapper.selectList(workOrderWrapper);
        List<WorkOrderTodoVO> workOrderTodoVOS = WorkOrderConverter.INSTANCE.toWorkTodoVOS(workOrders);
        return workOrderTodoVOS;
    }

    @Override
    public List<StatusDataVO> getStatus(StatusDataParam param) {
        TimeTypeEnum timeTypeEnum = TimeTypeEnum.getByValue(param.getTimeType());
        LocalDateTime createTimeFrom = null;
        LocalDateTime createTimeTo = LocalDateTime.now();
        switch (timeTypeEnum) {
            case WEEK -> createTimeFrom = LocalDateTime.now().minusWeeks(1L);
            case MONTH -> createTimeFrom = LocalDateTime.now().minusMonths(1L);
            case YEAR -> createTimeFrom = LocalDateTime.now().minusYears(1L);
        }
        QueryWrapper<WorkOrder> workOrderWrapper = WorkOrderQuery.getCountGroupByStatusByDate(createTimeFrom, createTimeTo);
        List<Map<String, Object>> statusMaps = workOrderMapper.selectMaps(workOrderWrapper);
        List<StatusDataVO> vos = new ArrayList<>();
        Map<Integer, Long> collect = new HashMap<>();
        statusMaps.forEach(map -> {
            Integer status = (Integer) map.get("status");
            Long count = (Long) map.get("count");
            collect.put(status, count);
        });
        for (WorkOrderStatusEnum statusEnum : WorkOrderStatusEnum.values()) {
            StatusDataVO statusDataVO = new StatusDataVO();
            statusDataVO.setStatus(statusEnum.getValue());
            statusDataVO.setStatusDesc(statusEnum.getDesc());
            statusDataVO.setQuantity(collect.getOrDefault(statusEnum.getValue(), 0L));
            vos.add(statusDataVO);
        }

        return vos;
    }

    @Override
    public List<TypeDataVO> getType(StatusDataParam param) {
        TimeTypeEnum timeTypeEnum = TimeTypeEnum.getByValue(param.getTimeType());
        LocalDateTime createTimeFrom = null;
        LocalDateTime createTimeTo = LocalDateTime.now();
        switch (timeTypeEnum) {
            case WEEK -> createTimeFrom = LocalDateTime.now().minusWeeks(1L);
            case MONTH -> createTimeFrom = LocalDateTime.now().minusMonths(1L);
            case YEAR -> createTimeFrom = LocalDateTime.now().minusYears(1L);
        }
        QueryWrapper<WorkOrder> workOrderWrapper = WorkOrderQuery.getCountGroupByTypeByDate(createTimeFrom, createTimeTo);
        List<Map<String, Object>> statusMaps = workOrderMapper.selectMaps(workOrderWrapper);
        List<TypeDataVO> vos = new ArrayList<>();
        Map<Integer, Long> collect = new HashMap<>();
        statusMaps.forEach(map -> {
            Integer status = (Integer) map.get("type");
            Long count = (Long) map.get("count");
            collect.put(status, count);
        });
        for (WorkOrderTypeEnum typeEnum : WorkOrderTypeEnum.values()) {
            TypeDataVO typeVO = new TypeDataVO();
            typeVO.setType(typeEnum.getValue());
            typeVO.setTypeDesc(typeEnum.getDesc());
            typeVO.setQuantity(collect.getOrDefault(typeEnum.getValue(), 0L));
            vos.add(typeVO);
        }
        return vos;
    }

    @Override
    public List<WeekHandleVO> getWeekHandleQuantity() {
        LocalDate today = LocalDate.now();
        List<WeekHandleVO> VOList = new ArrayList<>();

        LocalDate weekAgo = today.minusDays(6);

        LocalDateTime weekAgoTime = weekAgo.atStartOfDay();
        LocalDateTime todayEndTime = today.plusDays(1).atStartOfDay();
        LambdaQueryWrapper<HandleUserInfo> handleUserInfoWrapper = HandleUserInfoQuery.getByHandleDate(weekAgoTime, todayEndTime);
        List<HandleUserInfo> weekHandleUserInfos = handleUserInfoMapper.selectList(handleUserInfoWrapper);

        for (long i = 6L; i >= 0L; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime startOfDate = date.atStartOfDay();
            LocalDateTime endOfDate = date.plusDays(1L).atStartOfDay();

            List<HandleUserInfo> dateRecords = weekHandleUserInfos.stream()
                    .filter(record -> {
                        LocalDateTime recordTime = record.getHandleTime() != null ?
                                record.getHandleTime() :
                                record.getCreateTime();
                        return recordTime != null && !recordTime.isBefore(startOfDate) && recordTime.isBefore(endOfDate);
                    })
                    .collect(Collectors.toList());

            Map<Long, List<HandleUserInfo>> workOrderGroups = dateRecords.stream()
                    .collect(Collectors.groupingBy(HandleUserInfo::getOrderId));

            long dailyFinishedNum = 0;
            long dailyUnfinishedNum = 0;

            for (Map.Entry<Long, List<HandleUserInfo>> entry : workOrderGroups.entrySet()) {
                long workOrderId = entry.getKey();
                List<HandleUserInfo> handlers = entry.getValue();

                boolean allFinished = handlers.stream().allMatch(h -> h.getFinished() == true);

                if (allFinished) {
                    dailyFinishedNum++;
                } else {
                    dailyUnfinishedNum++;
                }
            }

            WeekHandleVO vo = new WeekHandleVO();
            vo.setDate(formatter.format(date));
            vo.setDailyTotalNum(dailyFinishedNum + dailyUnfinishedNum);
            vo.setDailyFinishedNum(dailyFinishedNum);
            VOList.add(vo);
        }

        return VOList;
    }

    @Override
    public IPage<MessageVO> pageMessages(MessageParam param) {
        Long receiverId = StaffHolder.get().getId();
        LambdaQueryWrapper<Message> wrapper = MessageQuery.getByReceiverIdWrapper(receiverId);
        IPage<Message> pageWrapper = new Page<>();
        pageWrapper.setSize(param.getPageSize());
        pageWrapper.setCurrent(param.getPageNum());
        IPage<Message> messageIPage = messageMapper.selectPage(pageWrapper, wrapper);
        IPage<MessageVO> pageMessageVOS = MessageConverter.INSTANCE.toPageMessageVOS(messageIPage);
        return pageMessageVOS;
    }
}
