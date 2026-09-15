package com.example.app.config;

import com.example.app.exception.BusinessException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String message = "参数校验失败";
        FieldError fieldError = e.getBindingResult().getFieldError();
        if (fieldError != null) {
            message = fieldError.getDefaultMessage();
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraint(ConstraintViolationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException e) {
        log.warn("Unhandled runtime exception: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(e.getMessage()));
    }

    private Map<String, Object> errorBody(String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);
        return body;
    }
}
