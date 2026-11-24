package com.canchaya.security;

import com.canchaya.domain.Role;

/**
 * El usuario "con sesión iniciada" que se guarda en el contexto de seguridad después de leer
 * un JWT válido. Los controladores lo reciben con @AuthenticationPrincipal.
 */
public record AuthenticatedUser(Long id, String email, Role role) {
}
