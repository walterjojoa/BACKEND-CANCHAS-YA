package com.canchaya.repository;

import com.canchaya.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * GRASP Pure Fabrication: "repository" is not a concept of the football-field business.
 * It is invented so that entities don't have to know about SQL or connections.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
