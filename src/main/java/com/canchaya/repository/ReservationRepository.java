package com.canchaya.repository;

import com.canchaya.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/** GRASP Pure Fabrication: persistence of reservations. */
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByFieldIdAndDate(Long fieldId, LocalDate date);

    List<Reservation> findByPlayerIdOrderByDateDescStartHourDesc(Long playerId);

    List<Reservation> findByFieldOwnerIdOrderByDateDescStartHourDesc(Long ownerId);
}
