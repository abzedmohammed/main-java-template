package com.abzed.template.common.exception;

import org.springframework.http.HttpStatus;

/** 400 Bad Request — invalid input or a violated business rule. */
public class BadRequestException extends ApiException {
    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
