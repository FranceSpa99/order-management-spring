package com.example.demo.event;

import com.example.demo.domain.OrderStatus;
import java.time.Instant;
import java.util.UUID;

public record OrderStatusChangedEvent(
        UUID eventId,
        String version,
        UUID orderId,
        OrderStatus previousStatus,
        OrderStatus newStatus,
        Instant timestamp
) {
}
