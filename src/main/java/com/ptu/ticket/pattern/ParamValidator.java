package com.ptu.ticket.pattern;

import com.ptu.ticket.entity.TicketOrder;
import org.springframework.stereotype.Component;

/**
 * 参数校验节点：校验下单入参是否合法（用户、车次等基本字段）。
 */
@Component
public class ParamValidator extends OrderValidator {
    @Override
    public void validate(TicketOrder order) {
        if (order.getUserId() == null || order.getUserId() <= 0) {
            throw new RuntimeException("用户ID非法！");
        }
        if (order.getTrainNumber() == null || order.getTrainNumber().isEmpty()) {
            throw new RuntimeException("车次号不能为空！");
        }
        if (next != null) {
            next.validate(order);
        }
    }
}
