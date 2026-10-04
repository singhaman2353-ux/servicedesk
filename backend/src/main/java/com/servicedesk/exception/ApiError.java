package com.servicedesk.exception;

import java.time.Instant;
import java.util.List;

/** The one JSON shape every error response uses. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> fieldErrors) {

    public record FieldViolation(String field, String message) {
    }
}