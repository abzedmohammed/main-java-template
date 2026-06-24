package com.abzed.template.common.exception;

import org.springframework.http.HttpStatus;

/** 403 Forbidden — authenticated but not permitted to perform the action. */
public class ForbiddenException extends ApiException {
    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
