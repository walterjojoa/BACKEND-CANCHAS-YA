package com.canchaya.service;

import com.canchaya.domain.Role;
import com.canchaya.domain.User;
import com.canchaya.dto.AuthDtos.AuthResponse;
import com.canchaya.dto.AuthDtos.LoginRequest;
import com.canchaya.dto.AuthDtos.RegisterRequest;
import com.canchaya.exception.BusinessException;
import com.canchaya.exception.ConflictException;
import com.canchaya.repository.UserRepository;
import com.canchaya.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de autenticación: registrarse e iniciar sesión.
 * GRASP Alta Cohesión: esta clase solo hace eso, nada de canchas ni reservas.
 */
@Service
public class AuthService {

    // Dependencias inyectadas por Spring a través del constructor.
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder; // BCrypt (definido en AppConfig)
    private final JwtService jwtService;           // genera el token de sesión

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Registra un usuario nuevo y le devuelve su token (queda con la sesión iniciada).
     * @Transactional: si algo falla, no se guarda nada en la BD.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Si no mandan rol, por defecto es jugador.
        Role role = request.role() == null ? Role.PLAYER : request.role();
        // Seguridad: nadie puede crearse a sí mismo como administrador.
        if (role == Role.ADMIN) {
            throw new BusinessException("ADMIN accounts cannot be self-registered");
        }
        String email = request.email().toLowerCase().trim();
        // El correo no puede estar repetido → error 409 Conflict.
        if (users.existsByEmail(email)) {
            throw new ConflictException("Email already registered: " + email);
        }
        // La contraseña se guarda cifrada con BCrypt, nunca en texto plano.
        User user = users.save(new User(request.name().trim(), email, passwordEncoder.encode(request.password()), role));
        return toResponse(user);
    }

    /**
     * Inicia sesión: busca el usuario por correo y compara la contraseña con el hash guardado.
     * Si el correo no existe o la clave no coincide, responde el MISMO error (401) para no
     * revelar qué correos están registrados.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(request.email().toLowerCase().trim())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return toResponse(user);
    }

    /** Arma la respuesta con el token JWT y los datos básicos del usuario. */
    private AuthResponse toResponse(User user) {
        return new AuthResponse(jwtService.generateToken(user), user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
