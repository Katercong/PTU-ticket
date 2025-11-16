package com.ptu.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ticket_route")
public class TicketRoute {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String trainNumber; // e.g., G123
    private Integer stock; // Remaining tickets
    private Double basePrice; // Base price
}
