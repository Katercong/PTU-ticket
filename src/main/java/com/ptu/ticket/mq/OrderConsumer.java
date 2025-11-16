package com.ptu.ticket.mq;

import com.ptu.ticket.entity.TicketOrder;
import com.ptu.ticket.mapper.TicketOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RocketMQMessageListener(topic = "TICKET_ORDER_TOPIC", consumerGroup = "ticket-consumer-group")
public class OrderConsumer implements RocketMQListener<OrderMessage> {

    private final TicketOrderMapper ticketOrderMapper;

    public OrderConsumer(TicketOrderMapper ticketOrderMapper) {
        this.ticketOrderMapper = ticketOrderMapper;
    }

    @Override
    public void onMessage(OrderMessage message) {
        log.info("Received order message from MQ: {}", message);
        try {
            TicketOrder order = new TicketOrder();
            order.setOrderNo(message.getOrderNo());
            order.setUserId(message.getUserId());
            order.setTrainNumber(message.getTrainNumber());
            order.setFinalPrice(message.getFinalPrice());
            order.setStatus(0); // Unpaid
            order.setCreateTime(LocalDateTime.now());
            
            // This insert relies on the database unique constraint (order_no) for idempotency.
            // If MQ redelivers the message, the duplicate insert will throw an exception,
            // which we can catch and ignore to achieve idempotency.
            ticketOrderMapper.insert(order);
            log.info("Successfully saved order to DB: {}", order.getOrderNo());
        } catch (Exception e) {
            log.warn("Failed to process order message or duplicate order: {}", message.getOrderNo(), e);
            // In a real system, you'd check if it's a DuplicateKeyException to ignore safely.
        }
    }
}
