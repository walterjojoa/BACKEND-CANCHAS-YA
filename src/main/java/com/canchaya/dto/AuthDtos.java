package com.canchaya.dto;

import com.canchaya.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request/response bodies for /api/auth. */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** role is optional: PLAYER by default. OWNER is allowed; ADMIN is not. */
    public record RegisterRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 72) String password,
            Role role) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record AuthResponse(String token, Long userId, String name, String email, Role role) {
    }
}
