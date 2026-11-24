package com.canchaya.exception;

/**
 * El usuario sí inició sesión, pero no tiene permiso sobre este recurso
 * (p. ej. cancelar la reserva de otro). Se convierte en HTTP 403 Forbidden.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
