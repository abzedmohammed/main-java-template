package com.abzed.template.common;

import com.abzed.template.common.exception.ApiException;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import jakarta.validation.ConstraintViolation;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Domain exceptions carry their own status. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiResponse.error(ex.getMessage(), ex.getStatus().value()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(GlobalExceptionHandler::validationMessage)
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(ApiResponse.error(message, 400));
    }

    /**
     * Messages written on the constraint ({@code @NotBlank(message = "...")}) are complete sentences and are
     * returned as-is. Built-in defaults ("must not be blank") need the field name to make sense, so they are prefixed.
     */
    private static String validationMessage(FieldError err) {
        boolean builtIn = err.contains(ConstraintViolation.class)
                && err.unwrap(ConstraintViolation.class).getMessageTemplate().startsWith("{");
        boolean custom = err.contains(ConstraintViolation.class) && !builtIn;
        return custom ? err.getDefaultMessage() : err.getField() + " " + err.getDefaultMessage();
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication failed", 401));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("You do not have permission to perform this action", 403));
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ApiResponse<Object>> handleJwt(JwtException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication failed", 401));
    }

    /** Legacy safety net — prefer the typed {@link ApiException} subclasses. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage(), 400));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred", 500));
    }
}
