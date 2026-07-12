package com.example.demo.controller;

import com.example.demo.dto.request.CreateOrderRequest;
import com.example.demo.dto.request.UpdateOrderStatusRequest;
import com.example.demo.dto.response.OrderResponse;
import com.example.demo.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable("id") UUID id){
        return orderService.getOrder(id);
    }

    @GetMapping()
    public Page<OrderResponse> getOrders(Pageable pageable) {
        return orderService.getOrders(pageable);
    }

    @PatchMapping("/{id}/status")
    public OrderResponse updateOrderStatus(@PathVariable("id") UUID id,
                                           @Valid @RequestBody UpdateOrderStatusRequest request) {

        return orderService.updateOrderStatus(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelOrder(@PathVariable("id") UUID id){

        orderService.cancelOrder(id);

    }

}