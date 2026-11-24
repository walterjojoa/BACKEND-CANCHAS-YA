package com.canchaya.exception;

/** El recurso pedido (cancha, reserva, usuario) no existe. Se convierte en HTTP 404 Not Found. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
