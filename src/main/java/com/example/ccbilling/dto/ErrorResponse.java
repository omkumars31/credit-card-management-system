package com.example.ccbilling.dto;

import java.time.Instant;
import java.util.Map;

/** Consistent error body. Never contains stack traces or internal details. */
public record ErrorResponse(Instant timestamp, int status, String code, String message, String path,
                            Map<String, String> fieldErrors) {

    public static ErrorResponse of(int status, String code, String message, String path) {
        return new ErrorResponse(Instant.now(), status, code, message, path, null);
    }

    public static ErrorResponse of(int status, String code, String message, String path, Map<String, String> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, code, message, path, fieldErrors);
    }
}
