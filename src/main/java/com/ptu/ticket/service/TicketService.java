package com.ptu.ticket.service;

import com.ptu.ticket.entity.Ticket;
import com.ptu.ticket.entity.TicketRoute;
import java.util.List;

public interface TicketService {
    TicketRoute getTicketInfo(String trainNumber);
    String buyTicket(Long userId, String trainNumber, String userType);
    List<Ticket> queryTickets(String fromStation, String toStation, String date);
}