package com.canchaya.security;

import com.canchaya.domain.Role;

/** The principal stored in the security context after a valid JWT is read. */
public record AuthenticatedUser(Long id, String email, Role role) {
}
