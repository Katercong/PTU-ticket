package com.ptu.ticket.config;

import com.ptu.ticket.pattern.BlacklistValidator;
import com.ptu.ticket.pattern.OrderValidator;
import com.ptu.ticket.pattern.ParamValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 组装订单校验责任链：ParamValidator -> BlacklistValidator
 * 校验节点通过 setNext 串联，调用链头即按顺序执行所有校验，消除原有冗余 if-else。
 */
@Configuration
public class OrderValidatorChainConfig {

    @Bean
    public OrderValidator orderValidatorChain(ParamValidator paramValidator,
                                              BlacklistValidator blacklistValidator) {
        // 串联责任链：先参数校验，再黑名单校验
        paramValidator.setNext(blacklistValidator);
        // 返回链头
        return paramValidator;
    }
}
