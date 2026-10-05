package com.servicedesk.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.HttpHeaders;

/**
 * Turns every failure into a clean {@link ApiError}. Clients never see stack traces,
 * SQL, class names or rejected input values.
 *
 * Note: validation exceptions are deliberately NOT logged, because their messages
 * contain the rejected values (which could be a password).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        ResponseEntity<ApiError> response =
                build(ex.getStatus(), ex.getCode(), ex.getMessage(), request, List.of());

        if (ex instanceof TooManyRequestsException tooMany) {
            return ResponseEntity.status(response.getStatusCode())
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(tooMany.getRetryAfterSeconds()))
                    .body(response.getBody());
        }

        return response;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldViolation(
                        fe.getField(),
                        Objects.requireNonNullElse(fe.getDefaultMessage(), "Invalid value")))
                .sorted(Comparator.comparing(ApiError.FieldViolation::field)
                        .thenComparing(ApiError.FieldViolation::message))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request data", request, violations);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
                                                     HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Malformed or unreadable request body", request, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Invalid value for parameter '" + ex.getName() + "'", request, List.of());
    }

    // Without these two, the catch-all below would turn security failures raised inside
    // controllers/services into 500 errors.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "You do not have permission to perform this action", request, List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required", request, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        log.warn("Data integrity violation on {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.CONFLICT, "CONFLICT",
                "The request conflicts with existing data", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAny(Exception ex, HttpServletRequest request) {
        // Spring MVC's own exceptions (404, 405, 415, ...) carry the right status themselves.
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            return switch (status.value()) {
                case 404 -> build(status, "NOT_FOUND", "Resource not found", request, List.of());
                case 405 -> build(status, "METHOD_NOT_ALLOWED", "HTTP method not supported", request, List.of());
                case 406, 415 -> build(status, "UNSUPPORTED_MEDIA_TYPE", "Unsupported media type", request, List.of());
                default -> status.is4xxClientError()
                        ? build(status, "BAD_REQUEST", "Request could not be processed", request, List.of())
                        : build(status, "INTERNAL_ERROR", "Unexpected server error", request, List.of());
            };
        }
        // Full details go to the server log only, never to the client.
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error",
                request, List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatusCode status, String code, String message,
                                           HttpServletRequest request,
                                           List<ApiError.FieldViolation> violations) {
        ApiError body = new ApiError(Instant.now(clock), status.value(), code, message,
                request.getRequestURI(), violations);
        return ResponseEntity.status(status).body(body);
    }
}