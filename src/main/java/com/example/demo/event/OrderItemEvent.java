package com.example.demo.event;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemEvent(
        UUID productId,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal) {
}
