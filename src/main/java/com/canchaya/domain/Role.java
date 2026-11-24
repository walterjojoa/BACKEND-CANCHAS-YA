package com.canchaya.domain;

/** Roles de usuario. Definen qué puede hacer cada uno en la API (ver SecurityConfig). */
public enum Role {
    /** Jugador: busca canchas, reserva y paga. */
    PLAYER,
    /** Dueño: publica canchas, define horarios y ve sus reservas. */
    OWNER,
    /** Administrador de la plataforma. */
    ADMIN
}
