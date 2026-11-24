package com.canchaya.config;

import com.canchaya.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configuración de seguridad: qué rutas son públicas, cuáles exigen token y qué rol,
 * cómo se cifran las contraseñas y desde qué páginas web se puede llamar la API (CORS).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
                // CSRF se desactiva porque la API no usa cookies de sesión sino tokens JWT.
                .csrf(AbstractHttpConfigurer::disable)
                // Activa CORS con la configuración del bean corsConfigurationSource (abajo).
                .cors(cors -> {})
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())) // consola H2
                // STATELESS: el servidor no guarda sesiones; cada petición trae su token.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Reglas de acceso, se revisan en orden (la primera que coincide gana):
                .authorizeHttpRequests(auth -> auth
                        // Públicas: login/registro, salud, consola H2 y página de error.
                        .requestMatchers("/api/auth/**", "/api/health", "/h2-console/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/wompi/events").permitAll() // webhook de Wompi (verificado con checksum)
                        // Consultar canchas es público (para ver el catálogo sin cuenta).
                        .requestMatchers(HttpMethod.GET, "/api/fields", "/api/fields/*", "/api/fields/*/availability").permitAll()
                        // Panel del dueño y crear/configurar canchas: solo OWNER.
                        .requestMatchers("/api/owner/**").hasRole("OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/fields", "/api/fields/*/schedules").hasRole("OWNER")
                        // Crear reservas: solo PLAYER.
                        .requestMatchers(HttpMethod.POST, "/api/reservations").hasRole("PLAYER")
                        // Todo lo demás: cualquier usuario con token válido.
                        .anyRequest().authenticated())
                // Sin token o token inválido → 401 (en vez de redirigir a un formulario de login).
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // Nuestro filtro JWT corre antes del filtro estándar de usuario/contraseña.
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Cifrado de contraseñas con BCrypt (hash lento y con sal, el estándar recomendado). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS: lista de sitios web (orígenes) que pueden llamar la API desde el navegador.
     * Se configura con la variable CORS_ALLOWED_ORIGINS separada por comas
     * (en Render incluye https://canchaya-frontend.onrender.com; en local, Vite en el puerto 5173).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${canchaya.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
