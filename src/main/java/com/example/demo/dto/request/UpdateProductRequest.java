package com.example.demo.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record UpdateProductRequest(

        String name,

        String description,

        @DecimalMin("0.01")
        BigDecimal price,

        @Min(0)
        Integer stockQuantity
) {}
