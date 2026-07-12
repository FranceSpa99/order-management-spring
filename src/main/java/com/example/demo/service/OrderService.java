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
import com.example.demo.exception.InsufficientStockException;
import com.example.demo.exception.InvalidStateTransitionException;
import com.example.demo.exception.OrderNotFoundException;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for customerId={}", request.customerId());

        Order order = new Order();
        order.setCustomerId(request.customerId());
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
        log.info("Order created id={} customerId={} total={}", saved.getId(), saved.getCustomerId(), saved.getTotalAmount());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
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
    public OrderResponse updateOrderStatus(UUID id, UpdateOrderStatusRequest request) {
        log.info("Updating order id={} to status={}", id, request.status());
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + id));

        if (!order.getStatus().canTransitionTo(request.status())) {
            log.warn("Invalid status transition for order id={}: {} -> {}", id, order.getStatus(), request.status());
            throw new InvalidStateTransitionException(
                    "Cannot transition from " + order.getStatus() + " to " + request.status()
            );
        }
        order.setStatus(request.status());

        return toResponse(order);
    }

    @Transactional
    public void cancelOrder(UUID id) {
        log.info("Cancelling order id={}", id);
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + id));

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
}
