package com.canchaya.repository;

import com.canchaya.domain.Field;
import com.canchaya.domain.Zone;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a la tabla de canchas.
 * GRASP Fabricación Pura: la persistencia de las canchas vive aquí, no dentro de {@link Field}.
 * <p>
 * Es solo una interfaz: Spring Data JPA crea la implementación sola. Las consultas se
 * deducen del NOMBRE del método (findBy... OrderBy...), sin escribir SQL.
 * JpaRepository ya trae save, findById, findAll, delete, count...
 */
public interface FieldRepository extends JpaRepository<Field, Long> {

    /** Canchas activas ordenadas por nombre (A→Z). */
    List<Field> findByActiveTrueOrderByNameAsc();

    /** Canchas activas de una zona, ordenadas por nombre. */
    List<Field> findByActiveTrueAndZoneOrderByNameAsc(Zone zone);

    /** Canchas de un dueño, ordenadas por nombre. */
    List<Field> findByOwnerIdOrderByNameAsc(Long ownerId);

    /**
     * Bloquea la fila de la cancha mientras se crea una reserva (SELECT ... FOR UPDATE),
     * para que dos jugadores no puedan reservar la misma hora al mismo tiempo.
     * El bloqueo se libera al terminar la transacción.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Field f where f.id = :id")
    Optional<Field> findByIdForUpdate(@Param("id") Long id);
}
