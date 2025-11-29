package com.ptu.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ptu.ticket.entity.Ticket;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {
}