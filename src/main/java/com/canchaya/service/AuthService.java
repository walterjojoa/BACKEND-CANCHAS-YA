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

/** GRASP High Cohesion: registration and login only. */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        Role role = request.role() == null ? Role.PLAYER : request.role();
        if (role == Role.ADMIN) {
            throw new BusinessException("ADMIN accounts cannot be self-registered");
        }
        String email = request.email().toLowerCase().trim();
        if (users.existsByEmail(email)) {
            throw new ConflictException("Email already registered: " + email);
        }
        User user = users.save(new User(request.name().trim(), email, passwordEncoder.encode(request.password()), role));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(request.email().toLowerCase().trim())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        return new AuthResponse(jwtService.generateToken(user), user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
