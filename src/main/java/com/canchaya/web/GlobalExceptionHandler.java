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
 * GRASP Pure Fabrication: translating exceptions into HTTP answers is a purely technical job,
 * so it lives in one invented class instead of a try/catch in every controller.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail conflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ProblemDetail forbidden(RuntimeException e) {
        return problem(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail badCredentials(BadCredentialsException e) {
        return problem(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail business(BusinessException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail unreadable(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request");
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
