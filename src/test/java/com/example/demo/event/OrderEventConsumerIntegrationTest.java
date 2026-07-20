package com.example.demo.event;

import com.example.demo.AbstractIntegrationTest;
import com.example.demo.domain.OrderStatus;
import com.example.demo.domain.OrderSummary;
import com.example.demo.repository.OrderSummaryRepository;
import com.example.demo.repository.ProcessedEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OrderEventConsumerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private OrderSummaryRepository orderSummaryRepository;
    @Autowired private ProcessedEventRepository processedEventRepository;

    @Value("${app.kafka.topics.order-created.name}")
    private String orderCreatedTopic;

    @Value("${app.kafka.topics.status-changed.name}")
    private String statusChangedTopic;

    @AfterEach
    void cleanUp() {
        orderSummaryRepository.deleteAll();
        processedEventRepository.deleteAll();
    }

    @Nested
    class OnOrderCreated {

        @Test
        void shouldCreateOrderSummaryInReadModel() {
            UUID orderId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();
            OrderCreatedEvent event = new OrderCreatedEvent(
                    UUID.randomUUID(), "1.0", orderId, customerId,
                    new BigDecimal("1999.98"), Instant.now(),
                    List.of(new OrderItemEvent(
                            UUID.randomUUID(), 2,
                            new BigDecimal("999.99"), new BigDecimal("1999.98")))
            );

            kafkaTemplate.send(orderCreatedTopic, orderId.toString(), event);

            await().atMost(10, SECONDS).untilAsserted(() -> {
                Optional<OrderSummary> summary = orderSummaryRepository.findByOrderId(orderId);
                assertThat(summary).isPresent();
                assertThat(summary.get().getCustomerId()).isEqualTo(customerId);
                assertThat(summary.get().getStatus()).isEqualTo(OrderStatus.CREATED);
                assertThat(summary.get().getTotalAmount()).isEqualByComparingTo("1999.98");
                assertThat(summary.get().getItemCount()).isEqualTo(1);
            });
        }

        @Test
        void duplicateEvent_shouldBeIdempotent() {
            UUID orderId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            OrderCreatedEvent event = new OrderCreatedEvent(
                    eventId, "1.0", orderId, UUID.randomUUID(),
                    new BigDecimal("100.00"), Instant.now(),
                    List.of(new OrderItemEvent(
                            UUID.randomUUID(), 1,
                            new BigDecimal("100.00"), new BigDecimal("100.00")))
            );

            kafkaTemplate.send(orderCreatedTopic, orderId.toString(), event);
            kafkaTemplate.send(orderCreatedTopic, orderId.toString(), event);

            await().atMost(10, SECONDS).untilAsserted(() ->
                    assertThat(orderSummaryRepository.findByOrderId(orderId)).isPresent()
            );

            assertThat(orderSummaryRepository.findAll())
                    .filteredOn(s -> orderId.equals(s.getOrderId()))
                    .hasSize(1);
        }
    }

    @Nested
    class OnStatusChanged {

        @Test
        void shouldUpdateStatusInReadModel() {
            UUID orderId = UUID.randomUUID();

            OrderSummary existing = new OrderSummary();
            existing.setOrderId(orderId);
            existing.setCustomerId(UUID.randomUUID());
            existing.setStatus(OrderStatus.CREATED);
            existing.setTotalAmount(new BigDecimal("500.00"));
            existing.setItemCount(1);
            orderSummaryRepository.save(existing);

            OrderStatusChangedEvent event = new OrderStatusChangedEvent(
                    UUID.randomUUID(), "1.0", orderId,
                    OrderStatus.CREATED, OrderStatus.CONFIRMED, Instant.now()
            );

            kafkaTemplate.send(statusChangedTopic, orderId.toString(), event);

            await().atMost(10, SECONDS).untilAsserted(() -> {
                OrderSummary updated = orderSummaryRepository.findByOrderId(orderId).orElseThrow();
                assertThat(updated.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            });
        }
    }
}
