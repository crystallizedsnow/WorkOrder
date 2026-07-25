package com.example.spring_vue_demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.spring_vue_demo.entity.Flow;
import com.example.spring_vue_demo.enums.ErrorCode;
import com.example.spring_vue_demo.enums.HandleUserInfoHandleTypeEnum;
import com.example.spring_vue_demo.exception.UserSideException;
import com.example.spring_vue_demo.mapper.FlowMapper;
import com.example.spring_vue_demo.param.Flow.*;
import com.example.spring_vue_demo.service.FlowService;
import com.example.spring_vue_demo.service.convert.FlowConverter;
import com.example.spring_vue_demo.utils.OrderCodeUtils;
import com.example.spring_vue_demo.vo.Flow.FlowCreateVO;
import com.example.spring_vue_demo.vo.Flow.FlowNodeVO;
import com.example.spring_vue_demo.vo.Flow.FlowVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author wtt
 * @date 2025/06/14
 */
@Service
@RequiredArgsConstructor
public class FlowServiceImpl extends ServiceImpl<FlowMapper, Flow> implements FlowService {
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    @Override
    @Transactional
    public FlowCreateVO create(FlowCreateParam param,Long flowId) {
        // 校验审核节点处理人是否重复
        checkAuditHandlerNotRepeat(param.getNodes());
        // 1. 先保存所有节点，获取数据库生成的ID
        List<Flow> allNodes = new ArrayList<>();
        if(flowId==null){
            flowId= OrderCodeUtils.generateFlowId();//编辑时用原来的flowId
        }
        // 处理审核节点
        Long finalFlowId = flowId;
        List<Flow> auditNodes = param.getNodes().stream()
                .map(node -> buildFlowParam(finalFlowId,param.getFlowName(),HandleUserInfoHandleTypeEnum.AUDIT.getValue(),
                           node.getHandlerId(),node.getHandlerName(),false))
                .collect(Collectors.toList());
        auditNodes.get(auditNodes.size() - 1).setIsLastNode(true);// 标记为终止节点
        allNodes.addAll(auditNodes);

        // 处理分配节点
        Flow distributeFlow =buildFlowParam(flowId,param.getFlowName(),HandleUserInfoHandleTypeEnum.DISTRIBUTE.getValue(),
                param.getDistributeNode().getHandlerId(),param.getDistributeNode().getHandlerName(),true);
        allNodes.add(distributeFlow);

        // 处理验收节点
        Flow checkFlow =buildFlowParam(flowId,param.getFlowName(),HandleUserInfoHandleTypeEnum.CHECK.getValue(),
                param.getCheckNode().getHandlerId(),param.getCheckNode().getHandlerName(),true);
        allNodes.add(checkFlow);

        // 2. 批量保存所有节点
        boolean b1= saveBatch(allNodes);

        // 3. 构建审核节点链式关系
        LambdaQueryWrapper<Flow> wrapper = new LambdaQueryWrapper<Flow>()
                .eq(Flow::getFlowId, flowId)
                .orderByAsc(Flow::getId);
        List<Flow> flowList = list(wrapper);
        auditNodes = flowList.stream().filter(flow -> Objects.equals(flow.getNodeType(), HandleUserInfoHandleTypeEnum.AUDIT.getValue())).collect(Collectors.toList());
        Long headId = auditNodes.get(0).getId();
        for (int i = 0; i < auditNodes.size(); i++) {
            Flow current = auditNodes.get(i);
            current.setHeadFlowId(headId);
            if (i - 1 >= 0) {
                Flow previous = auditNodes.get(i - 1);
                previous.setNextFlowId(current.getId());
            }

            // 最后一个审核节点指向分配节点
            if (i == auditNodes.size() - 1) {
                current.setIsLastNode(true);
            }
        }

        // 4. 更新节点关系
        boolean b2=updateBatchById(auditNodes);
        return new FlowCreateVO(flowId,b1&&b2);
    }

    private void checkAuditHandlerNotRepeat(List<FlowNode> nodes) {
        Set<Long>userSet=new HashSet<>();
        for(FlowNode node:nodes){
            if(userSet.contains(node.getHandlerId())){
                throw new UserSideException(ErrorCode.AUDIT_HANDLER_CAN_NOT_REPEAT);
            }
            userSet.add(node.getHandlerId());
        }
    }

    @Override
    @Transactional
    public FlowCreateVO update(FlowUpdateParam param) {
        //删除+重新插入
        FlowIdParam flowIdParam = new FlowIdParam(param.getFlowId());
        boolean b1=delete(flowIdParam);
        FlowCreateVO createVO = create(param,param.getFlowId());
        return createVO;
    }

    @Override
    @Transactional
    public boolean delete(FlowIdParam param) {
        LambdaQueryWrapper<Flow> wrapper = new LambdaQueryWrapper<Flow>()
                .eq(Flow::getFlowId, param.getFlowId());
        boolean b = remove(wrapper);
        return b;
    }

    @Override
    public FlowVO getByFlowId(FlowIdParam param) {
        LambdaQueryWrapper<Flow> wrapper = new LambdaQueryWrapper<Flow>()
                .eq(Flow::getFlowId, param.getFlowId());
        List<Flow> flowList = list(wrapper);
        FlowVO flowVOS = FlowConverter.INSTANCE.toFlowVO(flowList);
        return flowVOS;
    }
    @Override
    public FlowNodeVO getNextFlowNode(Long curHandlerId,Long flowId){
        LambdaQueryWrapper<Flow>wrapper=new LambdaQueryWrapper<Flow>()
                .eq(Flow::getHandlerId,curHandlerId)
                .eq(Flow::getFlowId,flowId)
                .eq(Flow::getNodeType,HandleUserInfoHandleTypeEnum.AUDIT.getValue());
        Flow flow=getOne(wrapper);
        if(flow.getIsLastNode()){
            return null;
        }
        if(flow==null){
            throw new UserSideException(ErrorCode.FLOW_ERROR_CURRENT_HANDLER_IS_WRONG);
        }
        Long nextNodeId=flow.getNextFlowId();
        LambdaQueryWrapper<Flow>wrapper1=new LambdaQueryWrapper<Flow>()
                .eq(Flow::getId,nextNodeId);
        Flow nextFlow=getOne(wrapper1);
        if(nextFlow==null){
            throw new UserSideException(ErrorCode.FLOW_ERROR_NEXT_NODE_IS_NULL);
        }
        return FlowConverter.INSTANCE.toNodeVO(nextFlow);
    }

    @Override
    public Page<FlowVO> page(FlowPageParam param) {
        int pageSize=param.getPageSize();
        int pageNum=param.getPageNum();
        QueryWrapper<Flow> wrapper = new QueryWrapper<>();
        wrapper.select("DISTINCT flow_id")
                .last("LIMIT " + (pageNum-1)*pageSize + "," + pageSize);
        List<Flow> list = list(wrapper);
        List<Long>flowIds=list.stream().distinct().map(Flow::getFlowId).toList();
        QueryWrapper<Flow>wrapperCount=new QueryWrapper<Flow>()
                .select("DISTINCT flow_id");
        Long total=count(wrapperCount);
        LambdaQueryWrapper<Flow>wrapper1=new LambdaQueryWrapper<Flow>()
                .in(CollectionUtils.isNotEmpty(flowIds),Flow::getFlowId,flowIds);
        List<Flow> flowList=this.list(wrapper1);
        Page<Flow> flowPage=new Page<>();
        flowPage.setRecords(flowList);
        flowPage.setSize(param.getPageSize());
        flowPage.setCurrent(param.getPageNum());
        flowPage.setTotal(total);
        Page<FlowVO> flowVOPage = FlowConverter.INSTANCE.toPageVO(flowPage);
        return flowVOPage;
    }

    private Flow buildFlowParam(Long flowId,String flowName,Integer nodeType,Long handlerId,String handlerName,Boolean isLastNode){
        Flow flow = new Flow();
        flow.setFlowId(flowId);
        flow.setFlowName(flowName);
        flow.setNodeType(nodeType);
        flow.setHandlerId(handlerId);
        flow.setHandlerName(handlerName);
        flow.setIsLastNode(isLastNode);
        return flow;
    }
}
