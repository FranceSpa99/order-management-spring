package com.example.demo.event;

import com.example.demo.dto.response.OrderItemResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        UUID orderId,
        UUID customerId,
        BigDecimal totalAmount,
        Instant timestamp,
        List<OrderItemEvent> items

) {
}
