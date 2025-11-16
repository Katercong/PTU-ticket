package com.ptu.ticket.controller;

import com.ptu.ticket.entity.TicketRoute;
import com.ptu.ticket.service.TicketService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ticket")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping("/query")
    public TicketRoute queryTicket(@RequestParam String trainNumber) {
        return ticketService.getTicketInfo(trainNumber);
    }

    @PostMapping("/buy")
    public String buyTicket(@RequestParam Long userId, 
                            @RequestParam String trainNumber, 
                            @RequestParam(defaultValue = "adult") String userType) {
        return ticketService.buyTicket(userId, trainNumber, userType);
    }
}
