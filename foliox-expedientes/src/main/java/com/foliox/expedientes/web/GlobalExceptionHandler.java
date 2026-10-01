package com.foliox.expedientes.web;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.foliox.common.error.ApiError;
import com.foliox.common.exception.ForbiddenOperationException;
import com.foliox.common.exception.ResourceNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

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

    /** Decision 4: undecodable body / invalid enum in body → 400 ApiError en español. */
    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ApiError> handleServerWebInput(
            ServerWebInputException ex, ServerWebExchange exchange) {
        ApiError error = ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "La solicitud no es válida: revisá el cuerpo o los parámetros enviados",
                path(exchange));
        return ResponseEntity.badRequest().body(error);
    }

    /** Decision 4: bad enum filter / bad UUID path variable → 400 ApiError en español. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, ServerWebExchange exchange) {
        ApiError error = ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Valor inválido para el parámetro '" + ex.getName() + "'",
                path(exchange));
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

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ApiError> handleForbidden(
            ForbiddenOperationException ex, ServerWebExchange exchange) {
        ApiError error = ApiError.of(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                ex.getMessage(),
                path(exchange));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            ResourceNotFoundException ex, ServerWebExchange exchange) {
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
