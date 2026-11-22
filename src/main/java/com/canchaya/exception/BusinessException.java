package com.canchaya.exception;

/** A business rule was broken (invalid hours, field closed, etc.). Mapped to HTTP 400. */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
