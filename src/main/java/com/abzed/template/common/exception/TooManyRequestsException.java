package com.abzed.template.common.exception;

import org.springframework.http.HttpStatus;

/** 429 Too Many Requests — rate limit or lockout threshold exceeded. */
public class TooManyRequestsException extends ApiException {
    public TooManyRequestsException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
