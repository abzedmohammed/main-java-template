package com.abzed.template.common.exception;

import org.springframework.http.HttpStatus;

/** 401 Unauthorized — authentication is missing or invalid. */
public class UnauthorizedException extends ApiException {
    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
