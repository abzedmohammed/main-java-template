package com.abzed.template.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for domain exceptions that carry an HTTP status.
 * Thrown from services and translated to the standard {@code ApiResponse}
 * envelope by {@link com.abzed.template.common.GlobalExceptionHandler}.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
