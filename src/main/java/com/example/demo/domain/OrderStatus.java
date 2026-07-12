package com.example.demo.domain;

import java.util.Map;
import java.util.Set;

public enum OrderStatus {

    CREATED, CONFIRMED, PAID, SHIPPED, DELIVERED, CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
            CREATED,   Set.of(CONFIRMED, CANCELLED),
            CONFIRMED, Set.of(PAID, CANCELLED),
            PAID,      Set.of(SHIPPED, CANCELLED),
            SHIPPED,   Set.of(DELIVERED),
            DELIVERED, Set.of(),
            CANCELLED, Set.of()
    );

    public boolean canTransitionTo(OrderStatus target) {
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
