package com.canchaya.web;

import com.canchaya.dto.AuthDtos.AuthResponse;
import com.canchaya.dto.AuthDtos.LoginRequest;
import com.canchaya.dto.AuthDtos.RegisterRequest;
import com.canchaya.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints de autenticación: /api/auth/register y /api/auth/login (públicos, sin token).
 * <p>
 * GRASP Controlador: es el primer objeto que recibe los eventos del sistema "registrarse" e
 * "iniciar sesión" que llegan desde la interfaz. No tiene lógica de negocio: solo delega en
 * {@link AuthService}.
 */
@RestController                 // clase que atiende peticiones HTTP y responde JSON
@RequestMapping("/api/auth")    // prefijo común de todas sus rutas
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/register → crea la cuenta y devuelve el token (201 Created).
     * @Valid aplica las validaciones del DTO (correo válido, clave de 6+ caracteres...).
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /** POST /api/auth/login → devuelve el token JWT si el correo y la clave son correctos. */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
