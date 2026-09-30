package com.foliox.usuarios.web;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.foliox.common.error.ApiError;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(
            MethodArgumentNotValidException ex, ServerWebExchange exchange) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(field -> fieldErrors.put(field.getField(), field.getDefaultMessage()));
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validación fallida",
                path(exchange),
                fieldErrors);
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiError> handleBindException(
            WebExchangeBindException ex, ServerWebExchange exchange) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(field -> fieldErrors.put(field.getField(), field.getDefaultMessage()));
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validación fallida",
                path(exchange),
                fieldErrors);
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatusException(
            ResponseStatusException ex, ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        HttpStatus resolved = status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        ApiError error = ApiError.of(
                resolved.value(),
                resolved.getReasonPhrase(),
                ex.getReason() == null ? resolved.getReasonPhrase() : ex.getReason(),
                path(exchange));
        return ResponseEntity.status(resolved).body(error);
    }

    @ExceptionHandler(com.foliox.common.exception.ForbiddenOperationException.class)
    public ResponseEntity<ApiError> handleForbidden(
            com.foliox.common.exception.ForbiddenOperationException ex, ServerWebExchange exchange) {
        ApiError error = ApiError.of(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                ex.getMessage(),
                path(exchange));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(com.foliox.common.exception.ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            com.foliox.common.exception.ResourceNotFoundException ex, ServerWebExchange exchange) {
        ApiError error = ApiError.of(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                path(exchange));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    private String path(ServerWebExchange exchange) {
        return exchange.getRequest().getPath().value();
    }
}
