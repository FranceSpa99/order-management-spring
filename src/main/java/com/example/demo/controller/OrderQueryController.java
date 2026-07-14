package com.example.demo.controller;

import com.example.demo.domain.OrderStatus;
import com.example.demo.dto.response.OrderSummaryResponse;
import com.example.demo.service.OrderSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderQueryController {

    private final OrderSummaryService orderSummaryService;

    @GetMapping("/summaries")
    public Page<OrderSummaryResponse> getOrderSummaryService(Pageable pageable,
                                                             @RequestParam(required = false) UUID customerId,
                                                             @RequestParam(required = false) OrderStatus status){

        return orderSummaryService.getOrderSummaries(customerId, status, pageable);
    }

}
