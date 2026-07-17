package com.example.demo.service;

import com.example.demo.domain.Order;
import com.example.demo.domain.OrderItem;
import com.example.demo.domain.OrderStatus;
import com.example.demo.domain.Product;
import com.example.demo.dto.request.CreateOrderRequest;
import com.example.demo.dto.request.OrderItemRequest;
import com.example.demo.dto.request.UpdateOrderStatusRequest;
import com.example.demo.dto.response.OrderItemResponse;
import com.example.demo.dto.response.OrderResponse;
import com.example.demo.event.OrderCreatedEvent;
import com.example.demo.event.OrderEventPublisher;
import com.example.demo.event.OrderItemEvent;
import com.example.demo.event.OrderStatusChangedEvent;
import com.example.demo.metrics.OrderMetrics;
import io.micrometer.core.instrument.Timer;
import com.example.demo.exception.InsufficientStockException;
import com.example.demo.exception.InvalidStateTransitionException;
import com.example.demo.exception.OrderNotFoundException;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final OrderMetrics orderMetrics;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, UUID customerId) {
        log.info("Creating order for customerId={}", customerId);
        Timer.Sample sample = orderMetrics.startTimer();

        Order order = new Order();
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.CREATED);

        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : request.items()) {
            Product product = productRepository.findById(itemReq.productId())
                    .orElseThrow(() -> new EntityNotFoundException("Product not found: " + itemReq.productId()));

            if (product.getStockQuantity() < itemReq.quantity()) {
                log.warn("Insufficient stock for productId={}, requested={}, available={}",
                        product.getId(), itemReq.quantity(), product.getStockQuantity());
                throw new InsufficientStockException("Insufficient stock for: " + product.getId());
            }
            product.setStockQuantity(product.getStockQuantity() - itemReq.quantity());

            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(itemReq.quantity()));

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProduct(product);
            item.setQuantity(itemReq.quantity());
            item.setUnitPrice(product.getPrice());
            item.setSubtotal(subtotal);

            order.getOrderItems().add(item);
            total = total.add(subtotal);
        }

        order.setTotalAmount(total);
        Order saved = orderRepository.save(order);

        publishOrderCreatedEventOnKafka(saved);

        orderMetrics.recordOrderCreated();
        orderMetrics.stopTimer(sample);

        log.info("Order created id={} customerId={} total={}", saved.getId(), saved.getCustomerId(), saved.getTotalAmount());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "orders", key = "#id")
    public OrderResponse getOrder(UUID id) {
        log.debug("Fetching order id={}", id);
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + id));

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(Pageable pageable) {
        log.debug("Fetching orders page={} size={}", pageable.getPageNumber(), pageable.getPageSize());
        return orderRepository.findAll(pageable)
                .map(this::toResponse);
    }

    @Transactional
    @CacheEvict(value = "orders", key = "#id")
    public OrderResponse updateOrderStatus(UUID id, UpdateOrderStatusRequest request) {
        log.info("Updating order id={} to status={}", id, request.status());

        OrderStatus targetStatus = request.status();
        if (targetStatus == OrderStatus.SHIPPED || targetStatus == OrderStatus.DELIVERED) {
            if (!isAdmin()) {
                log.warn("Non-admin attempted to set order id={} to status={}", id, targetStatus);
                throw new AccessDeniedException("Only admins can set status " + targetStatus);
            }
        }

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + id));

        OrderStatus previousStatus = order.getStatus();

        if (!order.getStatus().canTransitionTo(targetStatus)) {
            log.warn("Invalid status transition for order id={}: {} -> {}", id, order.getStatus(), targetStatus);
            throw new InvalidStateTransitionException(
                    "Cannot transition from " + order.getStatus() + " to " + targetStatus
            );
        }
        order.setStatus(targetStatus);

        publishStatusChangedEventOnKafka(order, previousStatus);

        return toResponse(order);
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @Transactional
    @CacheEvict(value = "orders", key = "#id")
    public void cancelOrder(UUID id) {
        log.info("Cancelling order id={}", id);
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + id));

        OrderStatus previousStatus = order.getStatus();

        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            log.warn("Cannot cancel order id={} in status={}", id, order.getStatus());
            throw new InvalidStateTransitionException("Cannot cancel order in status " + order.getStatus());
        }

        for (OrderItem item : order.getOrderItems()) {
            Product product = item.getProduct();
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
        }

        order.setStatus(OrderStatus.CANCELLED);
        log.info("Order id={} cancelled, stock restored for {} items", id, order.getOrderItems().size());

        publishStatusChangedEventOnKafka(order, previousStatus);
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getOrderItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getProduct().getId(),
                        item.getProduct().getName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getSubtotal()
                ))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                items
        );
    }


    private void publishOrderCreatedEventOnKafka(Order saved) {

        List<OrderItemEvent> items = saved.getOrderItems().stream()
                .map(item -> new OrderItemEvent(
                        item.getProduct().getId(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getSubtotal()
                ))
                .toList();

        OrderCreatedEvent eventToSend = new OrderCreatedEvent(
                UUID.randomUUID(),
                "1.0",
                saved.getId(),
                saved.getCustomerId(),
                saved.getTotalAmount(),
                Instant.now(),
                items
        );

        orderEventPublisher.publishOrderCreated(eventToSend);
    }


    private void publishStatusChangedEventOnKafka(Order order, OrderStatus previousStatus) {

        OrderStatusChangedEvent eventToSend = new OrderStatusChangedEvent(
                UUID.randomUUID(),
                "1.0",
                order.getId(),
                previousStatus,
                order.getStatus(),
                Instant.now()
        );

        orderEventPublisher.publishStatusChanged(eventToSend);
    }
}
