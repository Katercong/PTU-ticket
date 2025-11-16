package com.ptu.ticket.pattern;

import com.ptu.ticket.entity.TicketOrder;

public abstract class OrderValidator {
    protected OrderValidator next;

    public void setNext(OrderValidator next) {
        this.next = next;
    }

    public abstract void validate(TicketOrder order);
}
