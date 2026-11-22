package com.canchaya.service;

import com.canchaya.domain.Field;
import com.canchaya.domain.Reservation;
import com.canchaya.domain.User;
import com.canchaya.dto.ReservationDtos.CreateReservationRequest;
import com.canchaya.dto.ReservationDtos.ReservationResponse;
import com.canchaya.exception.BusinessException;
import com.canchaya.exception.ConflictException;
import com.canchaya.exception.ForbiddenException;
import com.canchaya.exception.NotFoundException;
import com.canchaya.notification.NotificationService;
import com.canchaya.policy.DepositPolicy;
import com.canchaya.repository.FieldRepository;
import com.canchaya.repository.ReservationRepository;
import com.canchaya.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Use cases of a reservation: book, list, cancel.
 * <p>
 * GRASP High Cohesion: it does NOT charge money (that is {@link PaymentService}) and does
 * NOT write e-mails (that is {@link NotificationService}).
 * GRASP Low Coupling: it depends on repository interfaces, a {@link DepositPolicy} interface
 * and the notification service, never on Wompi, SMTP or SQL.
 */
@Service
public class ReservationService {

    private final ReservationRepository reservations;
    private final FieldRepository fields;
    private final UserRepository users;
    private final DepositPolicy depositPolicy;
    private final NotificationService notifications;
    private final Clock clock;

    public ReservationService(ReservationRepository reservations, FieldRepository fields, UserRepository users,
                              DepositPolicy depositPolicy, NotificationService notifications, Clock clock) {
        this.reservations = reservations;
        this.fields = fields;
        this.users = users;
        this.depositPolicy = depositPolicy;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public ReservationResponse create(Long playerId, CreateReservationRequest request) {
        LocalDateTime start = LocalDateTime.of(request.date(), LocalTime.of(request.startHour(), 0));
        if (!start.isAfter(LocalDateTime.now(clock))) {
            throw new BusinessException("You can only book future time slots");
        }
        User player = users.findById(playerId)
                .orElseThrow(() -> new NotFoundException("User " + playerId + " not found"));
        // Lock the field so two concurrent requests cannot both pass the overlap check.
        Field field = fields.findByIdForUpdate(request.fieldId())
                .orElseThrow(() -> new NotFoundException("Field " + request.fieldId() + " not found"));

        boolean taken = reservations.findByFieldIdAndDate(field.getId(), request.date()).stream()
                .anyMatch(r -> r.overlaps(request.startHour(), request.endHour()));   // Expert
        if (taken) {
            throw new ConflictException("That time slot is already booked");
        }

        Reservation reservation = field.book(player, request.date(),                 // Creator
                request.startHour(), request.endHour(), depositPolicy);
        reservations.save(reservation);
        notifications.reservationCreated(reservation);                               // Indirection
        return ReservationResponse.from(reservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> listForPlayer(Long playerId) {
        return reservations.findByPlayerIdOrderByDateDescStartHourDesc(playerId).stream()
                .map(ReservationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> listForOwner(Long ownerId) {
        return reservations.findByFieldOwnerIdOrderByDateDescStartHourDesc(ownerId).stream()
                .map(ReservationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(Long userId, Long reservationId) {
        Reservation r = load(reservationId);
        if (!r.belongsTo(userId) && !r.getField().isOwnedBy(userId)) {
            throw new ForbiddenException("You cannot see this reservation");
        }
        return ReservationResponse.from(r);
    }

    @Transactional
    public ReservationResponse cancel(Long playerId, Long reservationId) {
        Reservation reservation = load(reservationId);
        reservation.cancelBy(playerId);        // Expert: the reservation validates its own rules
        notifications.reservationCancelled(reservation);
        return ReservationResponse.from(reservation);
    }

    private Reservation load(Long id) {
        return reservations.findById(id).orElseThrow(() -> new NotFoundException("Reservation " + id + " not found"));
    }
}
