package com.example.demo.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

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
        ProducerRecord<String, Object> record = buildRecord(
                orderCreatedTopic, event.orderId().toString(), event, OrderEventType.ORDER_CREATED);
        kafkaTemplate.send(record)
                .whenComplete((result, ex) -> {
                    if (ex != null) log.error("Failed to publish ORDER_CREATED orderId={}", event.orderId(), ex);
                    else log.info("ORDER_CREATED published orderId={} offset={}", event.orderId(), result.getRecordMetadata().offset());
                });
    }

    @Override
    public void publishStatusChanged(OrderStatusChangedEvent event) {
        OrderEventType eventType = resolveEventType(event);
        ProducerRecord<String, Object> record = buildRecord(
                statusChangedTopic, event.orderId().toString(), event, eventType);
        kafkaTemplate.send(record)
                .whenComplete((result, ex) -> {
                    if (ex != null) log.error("Failed to publish {} orderId={}", eventType, event.orderId(), ex);
                    else log.info("{} published orderId={} offset={}", eventType, event.orderId(), result.getRecordMetadata().offset());
                });
    }

    private ProducerRecord<String, Object> buildRecord(String topic, String key, Object value, OrderEventType eventType) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, value);
        record.headers()
                .add("event-type", eventType.name().getBytes(StandardCharsets.UTF_8))
                .add("event-version", "1.0".getBytes(StandardCharsets.UTF_8))
                .add("correlation-id", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8))
                .add("source", "order-management".getBytes(StandardCharsets.UTF_8));
        return record;
    }

    private OrderEventType resolveEventType(OrderStatusChangedEvent event) {
        return switch (event.newStatus()) {
            case CONFIRMED -> OrderEventType.ORDER_CONFIRMED;
            case PAID -> OrderEventType.ORDER_PAID;
            case SHIPPED -> OrderEventType.ORDER_SHIPPED;
            case DELIVERED -> OrderEventType.ORDER_DELIVERED;
            case CANCELLED -> OrderEventType.ORDER_CANCELLED;
            default -> throw new IllegalArgumentException("Unmapped status: " + event.newStatus());
        };
    }
}
