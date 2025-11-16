package com.ptu.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ticket_order")
public class TicketOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo; // Unique Order Number (Unique Constraint for Idempotency)
    private Long userId;
    private String trainNumber;
    private Double finalPrice;
    private Integer status; // 0: Unpaid, 1: Paid
    private LocalDateTime createTime;
}
