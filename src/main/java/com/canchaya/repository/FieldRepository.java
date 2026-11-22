package com.canchaya.repository;

import com.canchaya.domain.Field;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** GRASP Pure Fabrication: persistence of fields lives here, not inside {@link Field}. */
public interface FieldRepository extends JpaRepository<Field, Long> {

    List<Field> findByActiveTrueOrderByNameAsc();

    List<Field> findByOwnerIdOrderByNameAsc(Long ownerId);

    /**
     * Locks the field row while a reservation is being created, so two players
     * cannot book the same slot at the same time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Field f where f.id = :id")
    Optional<Field> findByIdForUpdate(@Param("id") Long id);
}
