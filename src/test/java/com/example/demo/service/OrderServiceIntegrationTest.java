package com.example.demo.service;

import com.example.demo.AbstractIntegrationTest;
import com.example.demo.domain.Order;
import com.example.demo.domain.OrderStatus;
import com.example.demo.domain.Product;
import com.example.demo.dto.request.CreateOrderRequest;
import com.example.demo.dto.request.OrderItemRequest;
import com.example.demo.dto.request.UpdateOrderStatusRequest;
import com.example.demo.dto.response.OrderResponse;
import com.example.demo.event.OrderEventPublisher;
import com.example.demo.exception.InsufficientStockException;
import com.example.demo.exception.InvalidStateTransitionException;
import com.example.demo.exception.OrderNotFoundException;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Transactional
class OrderServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;

    @MockBean private OrderEventPublisher eventPublisher;

    private UUID customerId;
    private Product product;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();

        product = new Product();
        product.setName("Laptop");
        product.setPrice(new BigDecimal("999.99"));
        product.setStockQuantity(10);
        productRepository.save(product);
    }

    @Nested
    class CreateOrder {

        @Test
        void withValidRequest_shouldPersistOrderAndDeductStock() {
            CreateOrderRequest request = new CreateOrderRequest(
                    List.of(new OrderItemRequest(product.getId(), 3))
            );

            OrderResponse response = orderService.createOrder(request, customerId);

            assertThat(response.id()).isNotNull();
            assertThat(response.status()).isEqualTo(OrderStatus.CREATED);
            assertThat(response.customerId()).isEqualTo(customerId);
            assertThat(response.totalAmount()).isEqualByComparingTo("2999.97");

            Product updated = productRepository.findById(product.getId()).orElseThrow();
            assertThat(updated.getStockQuantity()).isEqualTo(7);

            verify(eventPublisher).publishOrderCreated(any());
        }

        @Test
        void withInsufficientStock_shouldThrowAndNotPublishEvent() {
            product.setStockQuantity(2);
            productRepository.saveAndFlush(product);

            CreateOrderRequest request = new CreateOrderRequest(
                    List.of(new OrderItemRequest(product.getId(), 5))
            );

            assertThatThrownBy(() -> orderService.createOrder(request, customerId))
                    .isInstanceOf(InsufficientStockException.class);

            verify(eventPublisher, never()).publishOrderCreated(any());
        }

        @Test
        void withMultipleItems_shouldCalculateTotalAndPersistItems() {
            Product mouse = new Product();
            mouse.setName("Mouse");
            mouse.setPrice(new BigDecimal("50.00"));
            mouse.setStockQuantity(5);
            productRepository.save(mouse);

            CreateOrderRequest request = new CreateOrderRequest(List.of(
                    new OrderItemRequest(product.getId(), 2),  // 1999.98
                    new OrderItemRequest(mouse.getId(), 1)     // 50.00
            ));

            OrderResponse response = orderService.createOrder(request, customerId);

            assertThat(response.totalAmount()).isEqualByComparingTo("2049.98");
            assertThat(response.items()).hasSize(2);
        }
    }

    @Nested
    class UpdateOrderStatus {

        @Test
        void validTransition_shouldPersistNewStatus() {
            OrderResponse created = orderService.createOrder(
                    new CreateOrderRequest(List.of(new OrderItemRequest(product.getId(), 1))),
                    customerId
            );

            orderService.updateOrderStatus(created.id(), new UpdateOrderStatusRequest(OrderStatus.CONFIRMED));

            Order order = orderRepository.findById(created.id()).orElseThrow();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(eventPublisher).publishStatusChanged(any());
        }

        @Test
        void invalidTransition_shouldThrowAndLeaveStatusUnchanged() {
            OrderResponse created = orderService.createOrder(
                    new CreateOrderRequest(List.of(new OrderItemRequest(product.getId(), 1))),
                    customerId
            );

            // CREATED → PAID is invalid (must go via CONFIRMED first)
            assertThatThrownBy(() -> orderService.updateOrderStatus(
                    created.id(), new UpdateOrderStatusRequest(OrderStatus.PAID)))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        void orderNotFound_shouldThrow() {
            assertThatThrownBy(() -> orderService.updateOrderStatus(
                    UUID.randomUUID(), new UpdateOrderStatusRequest(OrderStatus.CONFIRMED)))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }

    @Nested
    class CancelOrder {

        @Test
        void fromCreated_shouldCancelAndRestoreStock() {
            OrderResponse created = orderService.createOrder(
                    new CreateOrderRequest(List.of(new OrderItemRequest(product.getId(), 4))),
                    customerId
            );

            orderService.cancelOrder(created.id());

            Order order = orderRepository.findById(created.id()).orElseThrow();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

            Product restored = productRepository.findById(product.getId()).orElseThrow();
            assertThat(restored.getStockQuantity()).isEqualTo(10); // 6 + 4 restored
        }

        @Test
        void fromDelivered_shouldThrow() {
            OrderResponse created = orderService.createOrder(
                    new CreateOrderRequest(List.of(new OrderItemRequest(product.getId(), 1))),
                    customerId
            );

            Order order = orderRepository.findById(created.id()).orElseThrow();
            order.setStatus(OrderStatus.DELIVERED);
            orderRepository.saveAndFlush(order);

            assertThatThrownBy(() -> orderService.cancelOrder(created.id()))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        void orderNotFound_shouldThrow() {
            assertThatThrownBy(() -> orderService.cancelOrder(UUID.randomUUID()))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }
}
