package com.canchaya.exception;

/** The request collides with existing data (time slot taken, email already used). Mapped to HTTP 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
