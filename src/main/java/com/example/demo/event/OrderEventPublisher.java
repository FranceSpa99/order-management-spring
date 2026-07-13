package com.example.demo.event;

public interface OrderEventPublisher {
    void publishOrderCreated(OrderCreatedEvent event);
    void publishStatusChanged(OrderStatusChangedEvent event);
}
