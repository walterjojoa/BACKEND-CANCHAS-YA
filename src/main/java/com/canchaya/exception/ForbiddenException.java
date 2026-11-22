package com.canchaya.exception;

/** The user is authenticated but is not allowed to touch this resource. Mapped to HTTP 403. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
