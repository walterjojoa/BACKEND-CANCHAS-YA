package com.canchaya.web;

import com.canchaya.exception.BusinessException;
import com.canchaya.exception.ConflictException;
import com.canchaya.exception.ForbiddenException;
import com.canchaya.exception.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * Convierte las excepciones en respuestas HTTP con el código correcto (404, 409, 403, 401, 400).
 * <p>
 * GRASP Fabricación Pura: traducir excepciones a respuestas HTTP es un trabajo puramente técnico,
 * así que vive en una sola clase inventada en vez de un try/catch en cada controlador.
 * La respuesta usa el formato estándar ProblemDetail (RFC 7807): {status, title, detail}.
 */
@RestControllerAdvice // se aplica a todos los controladores
public class GlobalExceptionHandler {

    /** Recurso que no existe → 404 Not Found. */
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** Choque con datos existentes (hora ya reservada, correo repetido) → 409 Conflict. */
    @ExceptionHandler(ConflictException.class)
    public ProblemDetail conflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Sin permiso (no es tu reserva, no es tu cancha, rol equivocado) → 403 Forbidden. */
    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ProblemDetail forbidden(RuntimeException e) {
        return problem(HttpStatus.FORBIDDEN, e.getMessage());
    }

    /** Correo o contraseña incorrectos → 401 Unauthorized. */
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail badCredentials(BadCredentialsException e) {
        return problem(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    /** Regla de negocio violada (cancha cerrada, horas inválidas...) → 400 Bad Request. */
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail business(BusinessException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Falló @Valid en un DTO → 400 con la lista "campo: error; campo: error". */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, detail);
    }

    /** JSON mal formado o parámetro de tipo equivocado (p. ej. zone=PLUTON) → 400. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail unreadable(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request");
    }

    /** Crea el cuerpo de error estándar. */
    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
