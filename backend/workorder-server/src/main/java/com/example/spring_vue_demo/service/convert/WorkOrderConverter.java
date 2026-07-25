package com.example.spring_vue_demo.service.convert;


import com.example.spring_vue_demo.entity.HandleUserInfo;
import com.example.spring_vue_demo.entity.WorkOrder;
import com.example.spring_vue_demo.vo.*;
import com.example.spring_vue_demo.vo.WorkOrder.WorkOrderDetailVO;
import com.example.spring_vue_demo.vo.WorkOrder.WorkOrderExportVO;
import com.example.spring_vue_demo.vo.WorkOrder.WorkOrderPageVO;
import com.example.spring_vue_demo.vo.WorkOrder.WorkOrderTodoVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface WorkOrderConverter {
    WorkOrderConverter INSTANCE=  Mappers.getMapper(WorkOrderConverter.class);

    List<WorkOrderPageVO> toPageVOS(List<WorkOrder> workOrders);

    @Mapping(target="priorityLevelDesc",expression = "java( com.example.spring_vue_demo.enums.WorkOrderPriorityLevelEnum.getByValue(workOrder.getPriorityLevel()).getDesc() )")
    @Mapping(target="statusDesc",expression = "java( com.example.spring_vue_demo.enums.WorkOrderStatusEnum.getByValue(workOrder.getStatus()).getDesc() )")
    @Mapping(target="typeDesc",expression = "java( com.example.spring_vue_demo.enums.WorkOrderTypeEnum.getByValue(workOrder.getType()).getDesc() )")
    WorkOrderPageVO toPageVO(WorkOrder workOrder);

    List<HandleUserInfoVO> toVOS(List<HandleUserInfo> pageHandleUserInfos);

    @Mapping(target="handleTypeDesc",expression = "java( com.example.spring_vue_demo.enums.HandleUserInfoHandleTypeEnum.getByValue(pageHandleUserInfo.getHandleType()).getDesc() )")
    @Mapping(target="finishedDesc",expression = "java( pageHandleUserInfo.getFinished()!=null? (pageHandleUserInfo.getFinished()?\"是\":\"否\") :null )")
    HandleUserInfoVO toVO(HandleUserInfo pageHandleUserInfo);

    @Mapping(target="priorityLevelDesc",expression = "java( com.example.spring_vue_demo.enums.WorkOrderPriorityLevelEnum.getByValue(workOrder.getPriorityLevel()).getDesc() )")
    @Mapping(target="statusDesc",expression = "java( com.example.spring_vue_demo.enums.WorkOrderStatusEnum.getByValue(workOrder.getStatus()).getDesc() )")
    @Mapping(target="typeDesc",expression = "java( com.example.spring_vue_demo.enums.WorkOrderTypeEnum.getByValue(workOrder.getType()).getDesc() )")
    WorkOrderDetailVO toDetailVO(WorkOrder workOrder);

    List<WorkOrderTodoVO> toWorkTodoVOS(List<WorkOrder> workOrders);
    @Mapping(target="statusDesc",expression = "java(workOrder.getStatus()!=null?com.example.spring_vue_demo.enums.WorkOrderStatusEnum.getByValue(workOrder.getStatus()).getDesc():null )")
    @Mapping(target="typeDesc",expression = "java( workOrder.getType()!=null?com.example.spring_vue_demo.enums.WorkOrderTypeEnum.getByValue(workOrder.getType()).getDesc():null )")
    WorkOrderTodoVO toWorkTodoVO(WorkOrder workOrder);

    List<WorkOrderExportVO> toExcelVOS(List<WorkOrderPageVO> pageVOS);

    @Mapping(target="submitter",expression = "java( pageVO.getSubmitterInfo()!=null?pageVO.getSubmitterInfo().getUserName():null )")
    @Mapping(target="distributer",expression = "java( pageVO.getDistributerInfo()!=null?pageVO.getDistributerInfo().getUserName():null )")
    @Mapping(target="auditors",expression = "java(pageVO.getAuditorInfo()!=null?pageVO.getAuditorInfo().stream().map(com.example.spring_vue_demo.vo.HandleUserInfoVO::getUserName).collect(java.util.stream.Collectors.joining(\"、\")):null )")
    @Mapping(target="checker",expression = "java(pageVO.getCheckerInfo()!=null?pageVO.getCheckerInfo().getUserName():null )")
    @Mapping(target="distributeTime",expression = "java(pageVO.getDistributerInfo()!=null && pageVO.getDistributerInfo().getHandleTime()!=null?pageVO.getDistributerInfo().getHandleTime().format(java.time.format.DateTimeFormatter.ofPattern(\"yyyy-MM-dd HH:mm:ss\")):null)")
    @Mapping(target="handlers",expression = "java(pageVO.getHandlerInfo()!=null?pageVO.getHandlerInfo().stream().map(com.example.spring_vue_demo.vo.HandleUserInfoVO::getUserName).collect(java.util.stream.Collectors.joining(\"、\")) :null )")
    @Mapping(target="checkTime",expression = "java( pageVO.getCheckerInfo()!=null && pageVO.getCheckerInfo().getHandleTime()!=null?pageVO.getCheckerInfo().getHandleTime().format(java.time.format.DateTimeFormatter.ofPattern(\"yyyy-MM-dd HH:mm:ss\")):null )")
    WorkOrderExportVO toExcelVO(WorkOrderPageVO pageVO);

}
