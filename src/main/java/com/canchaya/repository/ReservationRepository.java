package com.canchaya.repository;

import com.canchaya.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
