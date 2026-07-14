package com.example.demo.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        String version,
        UUID orderId,
        UUID customerId,
        BigDecimal totalAmount,
        Instant timestamp,
        List<OrderItemEvent> items
) {
}
