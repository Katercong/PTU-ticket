package com.ptu.ticket.pattern;

import org.springframework.stereotype.Component;

@Component("adultPriceStrategy")
public class AdultPriceStrategy implements PriceStrategy {
    @Override
    public Double calculatePrice(Double basePrice) {
        return basePrice; // No discount
    }
}
