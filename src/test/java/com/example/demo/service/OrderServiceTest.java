package com.example.demo.service;

import com.example.demo.domain.*;
import com.example.demo.dto.request.CreateOrderRequest;
import com.example.demo.dto.request.OrderItemRequest;
import com.example.demo.dto.request.UpdateOrderStatusRequest;
import com.example.demo.dto.response.OrderResponse;
import com.example.demo.event.OrderCreatedEvent;
import com.example.demo.event.OrderEventPublisher;
import com.example.demo.event.OrderStatusChangedEvent;
import com.example.demo.exception.InsufficientStockException;
import com.example.demo.exception.InvalidStateTransitionException;
import com.example.demo.exception.OrderNotFoundException;
import com.example.demo.metrics.OrderMetrics;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ProductRepository;
import io.micrometer.core.instrument.Timer;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private OrderEventPublisher eventPublisher;
    @Mock private OrderMetrics orderMetrics;
    @Mock private Timer.Sample timerSample;

    @InjectMocks private OrderService orderService;

    private UUID productId;
    private UUID customerId;
    private UUID orderId;
    private Product product;
    private Order order;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        product = new Product();
        product.setId(productId);
        product.setName("Laptop");
        product.setPrice(new BigDecimal("999.99"));
        product.setStockQuantity(10);

        order = new Order();
        order.setId(orderId);
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.CREATED);
        order.setTotalAmount(new BigDecimal("999.99"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class CreateOrder {

        @BeforeEach
        void setUp() {
            lenient().when(orderMetrics.startTimer()).thenReturn(timerSample);
            lenient().when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void withValidRequest_shouldSaveOrderAndPublishEvent() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));

            CreateOrderRequest request = new CreateOrderRequest(
                    List.of(new OrderItemRequest(productId, 2))
            );

            OrderResponse response = orderService.createOrder(request, customerId);

            assertThat(response).isNotNull();
            assertThat(response.customerId()).isEqualTo(customerId);
            assertThat(response.status()).isEqualTo(OrderStatus.CREATED);
            verify(orderRepository).save(any(Order.class));
            verify(eventPublisher).publishOrderCreated(any(OrderCreatedEvent.class));
            verify(orderMetrics).recordOrderCreated();
        }

        @Test
        void withValidRequest_shouldDeductStock() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));

            CreateOrderRequest request = new CreateOrderRequest(
                    List.of(new OrderItemRequest(productId, 3))
            );

            orderService.createOrder(request, customerId);

            assertThat(product.getStockQuantity()).isEqualTo(7); // 10 - 3
        }

        @Test
        void withMultipleItems_shouldCalculateTotalCorrectly() {
            Product product2 = new Product();
            UUID product2Id = UUID.randomUUID();
            product2.setId(product2Id);
            product2.setName("Mouse");
            product2.setPrice(new BigDecimal("50.00"));
            product2.setStockQuantity(5);

            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            when(productRepository.findById(product2Id)).thenReturn(Optional.of(product2));

            CreateOrderRequest request = new CreateOrderRequest(List.of(
                    new OrderItemRequest(productId, 2),    // 999.99 * 2 = 1999.98
                    new OrderItemRequest(product2Id, 1)    // 50.00 * 1 = 50.00
            ));

            orderService.createOrder(request, customerId);

            verify(orderRepository).save(argThat(o ->
                    o.getTotalAmount().compareTo(new BigDecimal("2049.98")) == 0
            ));
        }

        @Test
        void withInsufficientStock_shouldThrowAndNotSave() {
            product.setStockQuantity(1);
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));

            CreateOrderRequest request = new CreateOrderRequest(
                    List.of(new OrderItemRequest(productId, 5))
            );

            assertThatThrownBy(() -> orderService.createOrder(request, customerId))
                    .isInstanceOf(InsufficientStockException.class);

            verify(orderRepository, never()).save(any());
            verify(eventPublisher, never()).publishOrderCreated(any());
        }

        @Test
        void withProductNotFound_shouldThrow() {
            when(productRepository.findById(productId)).thenReturn(Optional.empty());

            CreateOrderRequest request = new CreateOrderRequest(
                    List.of(new OrderItemRequest(productId, 1))
            );

            assertThatThrownBy(() -> orderService.createOrder(request, customerId))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    class GetOrder {

        @Test
        void withValidId_shouldReturnResponse() {
            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setQuantity(1);
            item.setUnitPrice(product.getPrice());
            item.setSubtotal(product.getPrice());
            order.getOrderItems().add(item);

            when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.of(order));

            OrderResponse response = orderService.getOrder(orderId);

            assertThat(response.id()).isEqualTo(orderId);
            assertThat(response.status()).isEqualTo(OrderStatus.CREATED);
            assertThat(response.items()).hasSize(1);
        }

        @Test
        void withInvalidId_shouldThrowOrderNotFoundException() {
            when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrder(orderId))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }

    @Nested
    class UpdateOrderStatus {

        @Test
        void validTransition_shouldUpdateStatusAndPublishEvent() {
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            orderService.updateOrderStatus(orderId, new UpdateOrderStatusRequest(OrderStatus.CONFIRMED));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(eventPublisher).publishStatusChanged(any(OrderStatusChangedEvent.class));
        }

        @Test
        void invalidTransition_shouldThrowAndNotPublish() {
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            // CREATED → PAID non valido (deve passare prima da CONFIRMED)
            assertThatThrownBy(() -> orderService.updateOrderStatus(orderId,
                    new UpdateOrderStatusRequest(OrderStatus.PAID)))
                    .isInstanceOf(InvalidStateTransitionException.class);

            verify(eventPublisher, never()).publishStatusChanged(any());
        }

        @Test
        void shippedWithoutAdminRole_shouldThrowAccessDeniedException() {
            assertThatThrownBy(() -> orderService.updateOrderStatus(orderId,
                    new UpdateOrderStatusRequest(OrderStatus.SHIPPED)))
                    .isInstanceOf(AccessDeniedException.class);

            verify(orderRepository, never()).findById(any());
        }

        @Test
        void shippedWithAdminRole_shouldSucceed() {
            order.setStatus(OrderStatus.PAID);
            setAdminAuth();
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            orderService.updateOrderStatus(orderId, new UpdateOrderStatusRequest(OrderStatus.SHIPPED));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            verify(eventPublisher).publishStatusChanged(any(OrderStatusChangedEvent.class));
        }

        @Test
        void deliveredWithAdminRole_shouldSucceed() {
            order.setStatus(OrderStatus.SHIPPED);
            setAdminAuth();
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            orderService.updateOrderStatus(orderId, new UpdateOrderStatusRequest(OrderStatus.DELIVERED));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        }

        @Test
        void orderNotFound_shouldThrow() {
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.updateOrderStatus(orderId,
                    new UpdateOrderStatusRequest(OrderStatus.CONFIRMED)))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }

    @Nested
    class CancelOrder {

        @Test
        void fromCreated_shouldCancelAndRestoreStock() {
            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setQuantity(3);
            order.getOrderItems().add(item);

            when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.of(order));

            orderService.cancelOrder(orderId);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(product.getStockQuantity()).isEqualTo(13); // 10 + 3 ripristinati
            verify(eventPublisher).publishStatusChanged(any(OrderStatusChangedEvent.class));
        }

        @Test
        void fromDelivered_shouldThrowAndNotPublish() {
            order.setStatus(OrderStatus.DELIVERED);
            when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                    .isInstanceOf(InvalidStateTransitionException.class);

            verify(eventPublisher, never()).publishStatusChanged(any());
        }

        @Test
        void orderNotFound_shouldThrow() {
            when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }

    private void setAdminAuth() {
        var auth = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
