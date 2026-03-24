package com.terra.api.common.infrastructure.web;

import com.terra.api.common.domain.exception.BadRequestException;
import com.terra.api.common.domain.exception.ForbiddenException;
import com.terra.api.common.domain.exception.ResourceConflictException;
import com.terra.api.common.domain.exception.ResourceNotFoundException;
import com.terra.api.common.domain.exception.TooManyRequestsException;
import com.terra.api.common.infrastructure.i18n.MessageResolver;
import com.terra.api.security.domain.JwtAuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageResolver messageResolver;

    public GlobalExceptionHandler(MessageResolver messageResolver) {
        this.messageResolver = messageResolver;
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ResourceConflictException ex, HttpServletRequest request) {
        logApiCode(request, HttpStatus.CONFLICT.value(), ex.getCode());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.of(ex.getCode(), messageResolver.get(ex.getCode())));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        logApiCode(request, HttpStatus.NOT_FOUND.value(), ex.getCode());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.of(ex.getCode(), messageResolver.get(ex.getCode())));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        logApiCode(request, HttpStatus.BAD_REQUEST.value(), ex.getCode());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.of(ex.getCode(), messageResolver.get(ex.getCode())));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<Map<String, Object>> handleTooManyRequests(TooManyRequestsException ex, HttpServletRequest request) {
        logApiCode(request, HttpStatus.TOO_MANY_REQUESTS.value(), ex.getCode());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", ex.getCode());
        body.put("message", messageResolver.get(ex.getCode(), ex.getMessageArgs()));
        body.put("retryAfterSeconds", ex.getRetryAfterSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(body);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        logApiCode(request, HttpStatus.FORBIDDEN.value(), ex.getCode());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.of(ex.getCode(), messageResolver.get(ex.getCode())));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        logApiCode(request, HttpStatus.UNAUTHORIZED.value(), "auth.invalid_credentials");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.of("auth.invalid_credentials", messageResolver.get("auth.invalid_credentials")));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisabledUser(DisabledException ex, HttpServletRequest request) {
        logApiCode(request, HttpStatus.FORBIDDEN.value(), "auth.account_disabled");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.of("auth.account_disabled", messageResolver.get("auth.account_disabled")));
    }

    @ExceptionHandler(JwtAuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleJwtAuthentication(JwtAuthenticationException ex, HttpServletRequest request) {
        logApiCode(request, HttpStatus.UNAUTHORIZED.value(), ex.getCode());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.of(ex.getCode(), messageResolver.get(ex.getCode())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        logApiCode(request, HttpStatus.BAD_REQUEST.value(), "validation.failed");
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            String messageKey = fieldError.getDefaultMessage();
            errors.put(fieldError.getField(), messageResolver.get(messageKey));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "validation.failed");
        body.put("message", messageResolver.get("validation.failed"));
        body.put("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    private void logApiCode(HttpServletRequest request, int status, String code) {
        String path = request == null ? "unknown" : request.getRequestURI();
        log.info("[API-CODE] status={} code={} path={}", status, code, path);
    }
}
