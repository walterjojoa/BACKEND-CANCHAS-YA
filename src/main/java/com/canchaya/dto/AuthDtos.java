package com.canchaya.dto;

import com.canchaya.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTOs (objetos de transferencia de datos) de /api/auth: lo que entra y sale en JSON.
 * Las anotaciones (@NotBlank, @Email, @Size) se validan solas gracias a @Valid en el controlador.
 */
public final class AuthDtos {

    /** Clase contenedora: no se instancia, solo agrupa los records. */
    private AuthDtos() {
    }

    /** Cuerpo del registro. role es opcional: PLAYER por defecto. Se permite OWNER; ADMIN no. */
    public record RegisterRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 72) String password, // 72 = máximo que soporta BCrypt
            Role role) {
    }

    /** Cuerpo del login. */
    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    /** Respuesta de login/registro: el token JWT y los datos básicos del usuario (nunca la contraseña). */
    public record AuthResponse(String token, Long userId, String name, String email, Role role) {
    }
}
