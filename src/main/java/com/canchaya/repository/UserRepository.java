package com.canchaya.repository;

import com.canchaya.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Acceso a la tabla de usuarios.
 * GRASP Fabricación Pura: "repositorio" no es un concepto del negocio de las canchas.
 * Se inventa para que las entidades no tengan que saber de SQL ni de conexiones.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** Busca un usuario por correo (para el login). Optional = puede no existir. */
    Optional<User> findByEmail(String email);

    /** true si ya hay un usuario con ese correo (para el registro). */
    boolean existsByEmail(String email);
}
