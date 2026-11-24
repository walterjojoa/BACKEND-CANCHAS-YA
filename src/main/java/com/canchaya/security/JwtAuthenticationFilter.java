package com.canchaya.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro que corre una vez en cada petición, antes de llegar al controlador.
 * Lee la cabecera "Authorization: Bearer &lt;token&gt;" y, si el token es válido,
 * marca la petición como autenticada con el usuario y su rol.
 * Si no hay token o es inválido, la petición sigue como anónima y SecurityConfig decide si la deja pasar.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(PREFIX)) {
            // Se quita "Bearer " y se valida el token (firma y fecha de vencimiento).
            jwtService.parse(header.substring(PREFIX.length())).ifPresent(user -> {
                // Spring Security espera los roles con el prefijo "ROLE_" (p. ej. ROLE_OWNER).
                var authority = new SimpleGrantedAuthority("ROLE_" + user.role().name());
                var authentication = new UsernamePasswordAuthenticationToken(user, null, List.of(authority));
                // Desde aquí, el resto de la petición "sabe" quién es el usuario.
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        // Siempre se continúa con la cadena de filtros.
        chain.doFilter(request, response);
    }
}
