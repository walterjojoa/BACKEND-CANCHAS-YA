package com.canchaya.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Usuario de la plataforma (jugador, dueño o administrador). Tabla {@code users}. */
@Entity
@Table(name = "users")
public class User {

    /** Llave primaria generada por la base de datos. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre visible. */
    @Column(nullable = false, length = 120)
    private String name;

    /** Correo, usado para iniciar sesión. unique = true: no puede repetirse. */
    @Column(nullable = false, unique = true, length = 160)
    private String email;

    /** Contraseña cifrada con BCrypt. Nunca se guarda la contraseña en texto plano. */
    @Column(nullable = false)
    private String passwordHash;

    /** Rol del usuario (PLAYER, OWNER o ADMIN). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** Fecha de registro. */
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** Constructor vacío obligatorio para JPA. */
    protected User() {
        // requerido por JPA
    }

    /** Crea un usuario. El correo se normaliza (minúsculas, sin espacios) para evitar duplicados. */
    public User(String name, String email, String passwordHash, Role role) {
        this.name = name;
        this.email = email.toLowerCase().trim();
        this.passwordHash = passwordHash;
        this.role = role;
    }

    /** true si el usuario tiene exactamente ese rol. */
    public boolean hasRole(Role expected) {
        return role == expected;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
}
