package com.canchaya.repository;

import com.canchaya.domain.Reservation;
import com.canchaya.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Acceso a la tabla de reservas.
 * GRASP Fabricación Pura: la persistencia de las reservas. Spring Data genera las consultas
 * a partir del nombre de cada método.
 */
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** Reservas de una cancha en un día (para revisar cruces y la disponibilidad). */
    List<Reservation> findByFieldIdAndDate(Long fieldId, LocalDate date);

    /** Reservas de un jugador, de la más reciente a la más antigua. */
    List<Reservation> findByPlayerIdOrderByDateDescStartHourDesc(Long playerId);

    /** Reservas de todas las canchas de un dueño (navega reserva → cancha → dueño). */
    List<Reservation> findByFieldOwnerIdOrderByDateDescStartHourDesc(Long ownerId);

    /**
     * Pasa a CANCELLED las reservas pendientes de una cancha y día creadas antes de deadline.
     * Es un UPDATE condicionado en la base de datos: si un pago la confirmó, ya no coincide.
     * No limpia el contexto de persistencia: se llama con la cancha ya bloqueada y cargada.
     */
    @Modifying(flushAutomatically = true)
    @Query("update Reservation r set r.status = :cancelled where r.status = :pending"
            + " and r.createdAt < :deadline and r.field.id = :fieldId and r.date = :date")
    int releaseUnpaid(@Param("fieldId") Long fieldId, @Param("date") LocalDate date,
                      @Param("deadline") Instant deadline,
                      @Param("pending") ReservationStatus pending, @Param("cancelled") ReservationStatus cancelled);

    /** Igual que {@link #releaseUnpaid} pero para todas las canchas (lo usa el job de vencimiento). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Reservation r set r.status = :cancelled where r.status = :pending and r.createdAt < :deadline")
    int releaseAllUnpaid(@Param("deadline") Instant deadline,
                         @Param("pending") ReservationStatus pending, @Param("cancelled") ReservationStatus cancelled);
}
