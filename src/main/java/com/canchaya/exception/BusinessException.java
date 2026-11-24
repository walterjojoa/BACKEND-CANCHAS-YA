package com.canchaya.exception;

/**
 * Se rompió una regla de negocio (horas inválidas, cancha cerrada, etc.).
 * GlobalExceptionHandler la convierte en HTTP 400 Bad Request.
 * Es RuntimeException para no obligar a declararla con "throws" en cada método.
 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
