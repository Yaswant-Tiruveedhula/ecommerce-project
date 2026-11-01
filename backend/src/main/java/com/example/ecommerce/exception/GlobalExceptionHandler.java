package com.example.ecommerce.exception;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
        return response(exception.getStatus(), exception.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception,
                                                   HttpServletRequest request) {
        Map<String, String> fields = new TreeMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return response(HttpStatus.BAD_REQUEST, "Request validation failed", fields, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception,
                                                            HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Request validation failed", Map.of(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> handleDataConflict(DataIntegrityViolationException exception,
                                                     HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "The request conflicts with existing data", Map.of(), request);
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String message,
                                                   Map<String, String> fields, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, fields, request.getRequestURI()));
    }

    public record ErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String message,
            Map<String, String> fieldErrors,
            String path) {
    }
}
