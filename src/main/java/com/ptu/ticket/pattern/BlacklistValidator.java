package com.ptu.ticket.pattern;

import com.ptu.ticket.entity.TicketOrder;
import org.springframework.stereotype.Component;

@Component
public class BlacklistValidator extends OrderValidator {
    @Override
    public void validate(TicketOrder order) {
        if (order.getUserId() == -1L) {
            throw new RuntimeException("User is in blacklist!");
        }
        if (next != null) {
            next.validate(order);
        }
    }
}
