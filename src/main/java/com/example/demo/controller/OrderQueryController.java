package com.example.demo.controller;

import com.example.demo.domain.OrderStatus;
import com.example.demo.dto.response.OrderSummaryResponse;
import com.example.demo.service.OrderSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Queries", description = "CQRS read model — order summaries from the denormalized read store")
@SecurityRequirement(name = "bearer-jwt")
public class OrderQueryController {

    private final OrderSummaryService orderSummaryService;

    @Operation(summary = "List order summaries from read model (paginated, filterable)")
    @ApiResponse(responseCode = "200", description = "Page of order summaries")
    @GetMapping("/summaries")
    public Page<OrderSummaryResponse> getOrderSummaries(
            Pageable pageable,
            @Parameter(description = "Filter by customer UUID") @RequestParam(required = false) UUID customerId,
            @Parameter(description = "Filter by order status") @RequestParam(required = false) OrderStatus status) {
        return orderSummaryService.getOrderSummaries(customerId, status, pageable);
    }
}
