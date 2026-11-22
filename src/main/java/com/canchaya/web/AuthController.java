package com.canchaya.web;

import com.canchaya.dto.AuthDtos.AuthResponse;
import com.canchaya.dto.AuthDtos.LoginRequest;
import com.canchaya.dto.AuthDtos.RegisterRequest;
import com.canchaya.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * GRASP Controller: first object that receives the system events "register" and "login"
 * coming from the UI. It does no business logic: it only delegates to {@link AuthService}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
