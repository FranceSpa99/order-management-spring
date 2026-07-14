package com.example.demo.event;

import com.example.demo.service.OrderSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final OrderSummaryService orderSummaryService;

    @KafkaListener(topics = "${app.kafka.topics.order-created.name}")
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Received ORDER_CREATED eventId={}", event.eventId());
        orderSummaryService.handleOrderCreated(event);
    }

    @KafkaListener(topics = "${app.kafka.topics.status-changed.name}")
    public void onStatusChanged(OrderStatusChangedEvent event) {
        log.info("Received STATUS_CHANGED eventId={}", event.eventId());
        orderSummaryService.handleStatusChanged(event);
    }

}
