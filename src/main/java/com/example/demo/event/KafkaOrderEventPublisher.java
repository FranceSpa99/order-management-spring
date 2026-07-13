package com.example.demo.event;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaOrderEventPublisher implements OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.order-created.name}")
    private String orderCreatedTopic;

    @Value("${app.kafka.topics.status-changed.name}")
    private String statusChangedTopic;

    @Override
    public void publishOrderCreated(OrderCreatedEvent event) {
        kafkaTemplate.send(orderCreatedTopic, event.orderId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) log.error("Failed to publish event orderId={}", event.orderId(), ex);
                else log.info("Event published orderId={} offset={}", event.orderId(), result.getRecordMetadata().offset());
            });
    }

    @Override
    public void publishStatusChanged(OrderStatusChangedEvent event) {
        kafkaTemplate.send(statusChangedTopic, event.orderId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) log.error("Failed to publish event orderId={}", event.orderId(), ex);
                else log.info("Event published orderId={} offset={}", event.orderId(), result.getRecordMetadata().offset());
            });
    }

}
