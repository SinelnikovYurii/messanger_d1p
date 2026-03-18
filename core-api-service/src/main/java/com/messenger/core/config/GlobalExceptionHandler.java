package com.messenger.core.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** 404 — сущность не найдена */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(
            RuntimeException ex, WebRequest request) {

        log.error("=== RuntimeException перехвачено ===");
        log.error("Request: {}", request.getDescription(false));
        log.error("Exception type: {}", ex.getClass().getSimpleName());
        log.error("Exception message: {}", ex.getMessage());
        log.error("Stack trace:", ex);
        log.error("=====================================");

        if ("Chat not found".equals(ex.getMessage())) {
            return buildResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), ex.getClass().getSimpleName());
        }

        if (ex instanceof IllegalArgumentException) {
            String msg = ex.getMessage();
            if (msg != null && (msg.contains("Ошибка авторизации")
                    || msg.contains("Пользователь не аутентифицирован"))) {
                return buildResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", msg, ex.getClass().getSimpleName());
            }
            return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", msg, ex.getClass().getSimpleName());
        }

        if (ex instanceof IllegalStateException) {
            return buildResponse(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), ex.getClass().getSimpleName());
        }

        return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), ex.getClass().getSimpleName());
    }

    private ResponseEntity<Object> buildResponse(HttpStatus status, String error, String message, String exType) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        body.put("exceptionType", exType);
        return new ResponseEntity<>(body, status);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAllExceptions(
            Exception ex, WebRequest request) {

        log.error("=== General Exception перехвачено ===");
        log.error("Request: {}", request.getDescription(false));
        log.error("Exception type: {}", ex.getClass().getSimpleName());
        log.error("Exception message: {}", ex.getMessage());
        log.error("Stack trace:", ex);
        log.error("======================================");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        body.put("message", "Произошла внутренняя ошибка сервера");
        body.put("details", ex.getMessage());
        body.put("exceptionType", ex.getClass().getSimpleName());

        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
