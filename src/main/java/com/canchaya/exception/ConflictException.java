package com.canchaya.exception;

/**
 * La petición choca con datos que ya existen (hora ya reservada, correo ya registrado).
 * Se convierte en HTTP 409 Conflict.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
