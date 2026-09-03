package com.example.spring_vue_demo.service.producer;

import com.alibaba.fastjson.JSON;
import com.example.spring_vue_demo.entity.WorkOrderMessageDto;
import com.example.spring_vue_demo.enums.WorkOrderStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * @author wtt
 * @date 2025/06/17
 */
@Component
@Slf4j
public class WorkOrderMessageProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void sendWorkOrderMessages(Integer status, String workOrderCode,
                                      List<Long> receiverIds, Long senderId, boolean finished) {
        WorkOrderMessageDto messageDto = new WorkOrderMessageDto();
        messageDto.setStatus(status);
        messageDto.setWorkOrderCode(workOrderCode);
        messageDto.setReceiverIds(receiverIds);
        messageDto.setFinished(finished);
        messageDto.setSenderId(senderId);
        log.info("Sending message to queue: {}", messageDto);
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
        rabbitTemplate.convertAndSend("workOrder.message.queue", messageDto, correlationData);
    }
}
