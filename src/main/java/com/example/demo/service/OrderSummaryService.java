package com.example.demo.service;

import com.example.demo.domain.OrderStatus;
import com.example.demo.domain.OrderSummary;
import com.example.demo.domain.ProcessedEvent;
import com.example.demo.event.OrderCreatedEvent;
import com.example.demo.event.OrderStatusChangedEvent;
import com.example.demo.exception.OrderSummaryNotFoundException;
import com.example.demo.repository.OrderSummaryRepository;
import com.example.demo.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSummaryService {

    private final OrderSummaryRepository orderSummaryRepository;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.warn("Duplicate event ignored eventId={}", event.eventId());
            return;
        }

        OrderSummary summary = new OrderSummary();
        summary.setOrderId(event.orderId());
        summary.setCustomerId(event.customerId());
        summary.setStatus(OrderStatus.CREATED);
        summary.setTotalAmount(event.totalAmount());
        summary.setItemCount(event.items().size());

        orderSummaryRepository.save(summary);
        processedEventRepository.save(new ProcessedEvent(event.eventId(), Instant.now()));

        log.info("OrderSummary created orderId={}", event.orderId());
    }

    @Transactional
    public void handleStatusChanged(OrderStatusChangedEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.warn("Duplicate event ignored eventId={}", event.eventId());
            return;
        }

        OrderSummary summary = orderSummaryRepository.findByOrderId(event.orderId())
                .orElseThrow(() -> new OrderSummaryNotFoundException("Order summary not found: " + event.orderId()));

        summary.setStatus(event.newStatus());
        orderSummaryRepository.save(summary);

        processedEventRepository.save(new ProcessedEvent(event.eventId(), Instant.now()));


    }
}
