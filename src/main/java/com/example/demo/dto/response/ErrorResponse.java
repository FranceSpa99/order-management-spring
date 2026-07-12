package com.example.demo.dto.response;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        int status,
        String message,
        Instant timestamp,
        List<String> errors
) {
    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(status, message, Instant.now(), List.of());
    }

    public static ErrorResponse of(int status, String message, List<String> errors) {
        return new ErrorResponse(status, message, Instant.now(), errors);
    }
}
