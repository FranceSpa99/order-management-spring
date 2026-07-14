package com.example.demo.exception;

public class OrderSummaryNotFoundException extends RuntimeException {
    public OrderSummaryNotFoundException(String message) {
        super(message);
    }
}
