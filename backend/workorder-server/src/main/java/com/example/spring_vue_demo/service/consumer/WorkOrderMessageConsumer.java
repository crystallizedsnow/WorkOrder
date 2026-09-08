package com.example.spring_vue_demo.service.consumer;

import com.example.spring_vue_demo.entity.WorkOrderMessageDto;
import com.example.spring_vue_demo.enums.WorkOrderStatusEnum;
import com.example.spring_vue_demo.mapper.MessageMapper;
import com.example.spring_vue_demo.service.helper.WorkOrderHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.BatchResult;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.example.spring_vue_demo.entity.Message;
import java.util.List;
import java.util.Objects;

/**
 * @author wtt
 * @date 2025/06/17
 */
@Component
@Slf4j
public class WorkOrderMessageConsumer {

    @Autowired
    private WorkOrderHelper workOrderHelper;

    @Autowired
    private MessageMapper messageMapper;

    @RabbitListener(queues = "workOrder.message.queue")
    public void handleWorkOrderMessage(WorkOrderMessageDto messageDto) {
        log.info("Received message: {}", messageDto);
        // 构建消息
        List<Message> messages = workOrderHelper.buildMessages(
                messageDto.getWorkOrderId(),
                Objects.requireNonNull(WorkOrderStatusEnum.getByValue(messageDto.getStatus())),
                messageDto.getWorkOrderCode(),
                messageDto.getReceiverIds(),
                messageDto.getSenderId(),
                messageDto.getFinished()
        );

        // 插入消息
        List<BatchResult> msgSuccess = messageMapper.insert(messages);
    }
}
