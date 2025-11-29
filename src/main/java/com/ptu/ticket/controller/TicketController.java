package com.ptu.ticket.controller;

import com.ptu.ticket.entity.Ticket;
import com.ptu.ticket.entity.TicketRoute;
import com.ptu.ticket.service.TicketService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping("/api/ticket/query")
    public TicketRoute queryTicket(@RequestParam String trainNumber) {
        return ticketService.getTicketInfo(trainNumber);
    }

    @GetMapping("/api/tickets/query")
    public List<Ticket> queryTickets(@RequestParam String fromStation,
                                     @RequestParam String toStation,
                                     @RequestParam String date) {
        return ticketService.queryTickets(fromStation, toStation, date);
    }

    @PostMapping("/api/ticket/buy")
    public String buyTicket(@RequestParam Long userId, 
                            @RequestParam String trainNumber, 
                            @RequestParam(defaultValue = "adult") String userType) {
        return ticketService.buyTicket(userId, trainNumber, userType);
    }
}