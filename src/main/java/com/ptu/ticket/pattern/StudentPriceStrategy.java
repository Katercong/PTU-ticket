package com.ptu.ticket.pattern;

import org.springframework.stereotype.Component;

@Component("studentPriceStrategy")
public class StudentPriceStrategy implements PriceStrategy {
    @Override
    public Double calculatePrice(Double basePrice) {
        return basePrice * 0.75; // 25% discount for students
    }
}
