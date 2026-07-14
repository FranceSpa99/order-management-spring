package com.example.demo.dto.response;

import com.example.demo.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummaryResponse(
        UUID id,
        UUID orderId,
        UUID customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        int itemCount,
        Instant lastUpdated
) {
}
