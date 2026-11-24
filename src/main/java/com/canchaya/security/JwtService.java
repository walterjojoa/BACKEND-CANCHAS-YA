package com.canchaya.security;

import com.canchaya.domain.Role;
import com.canchaya.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Genera y lee los tokens JWT de sesión.
 * <p>
 * GRASP Fabricación Pura: emitir y leer tokens es un asunto técnico, no de negocio,
 * así que tiene su propia clase inventada en vez de ensuciar {@link User}.
 */
@Service
public class JwtService {

    private final SecretKey key;         // clave para firmar (HMAC-SHA)
    private final Duration expiration;   // cuánto dura un token

    /**
     * El secreto viene de la variable JWT_SECRET (en Render la genera el propio Render).
     * Debe tener mínimo 32 caracteres (256 bits) para que la firma HMAC-SHA256 sea segura.
     */
    public JwtService(@Value("${canchaya.jwt.secret}") String secret,
                      @Value("${canchaya.jwt.expiration-hours:24}") long expirationHours) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("canchaya.jwt.secret must be at least 32 characters long");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expiration = Duration.ofHours(expirationHours);
    }

    /** Crea un token firmado con el id (subject), correo y rol del usuario; vence en 24 h por defecto. */
    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    /**
     * Devuelve el usuario autenticado si el token es válido y no ha vencido.
     * Si la firma no coincide (token alterado) o ya venció, devuelve vacío.
     */
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new AuthenticatedUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("email", String.class),
                    Role.valueOf(claims.get("role", String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
