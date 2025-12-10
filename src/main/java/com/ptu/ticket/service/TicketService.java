package com.ptu.ticket.service;

import com.ptu.ticket.entity.Ticket;
import com.ptu.ticket.entity.TicketRoute;
import java.util.List;

public interface TicketService {
    // M2: 旁路缓存 (Cache-Aside) & Redisson 分布式锁 (防止缓存击穿)
    TicketRoute getTicketInfo(String trainNumber);
    String buyTicket(Long userId, String trainNumber, String userType);
    List<Ticket> queryTickets(String fromStation, String toStation, String date);
}