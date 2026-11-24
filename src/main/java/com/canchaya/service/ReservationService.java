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
 * Casos de uso de una reserva: reservar, listar y cancelar.
 * <p>
 * GRASP Alta Cohesión: NO cobra dinero (eso es {@link PaymentService}) y NO escribe
 * correos (eso es {@link NotificationService}).
 * GRASP Bajo Acoplamiento: depende de interfaces de repositorio, de la interfaz
 * {@link DepositPolicy} y del servicio de notificaciones; nunca de Wompi, SMTP o SQL.
 */
@Service
public class ReservationService {

    private final ReservationRepository reservations;
    private final FieldRepository fields;
    private final UserRepository users;
    private final DepositPolicy depositPolicy;
    private final NotificationService notifications;
    /** Reloj inyectado (en vez de usar la hora del sistema directo) para poder fijar la hora en los tests. */
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

    /**
     * Crea una reserva (caso de uso principal del sistema).
     * Pasos: validar que sea a futuro → bloquear la cancha → revisar cruces → la cancha crea
     * la reserva → guardar → notificar.
     */
    @Transactional
    public ReservationResponse create(Long playerId, CreateReservationRequest request) {
        // 1. No se puede reservar en el pasado (ni la hora que ya empezó).
        LocalDateTime start = LocalDateTime.of(request.date(), LocalTime.of(request.startHour(), 0));
        if (!start.isAfter(LocalDateTime.now(clock))) {
            throw new BusinessException("You can only book future time slots");
        }
        User player = users.findById(playerId)
                .orElseThrow(() -> new NotFoundException("User " + playerId + " not found"));
        // 2. Se bloquea la cancha (SELECT ... FOR UPDATE) para que dos peticiones al mismo
        //    tiempo no pasen las dos la validación de cruce y reserven la misma hora.
        Field field = fields.findByIdForUpdate(request.fieldId())
                .orElseThrow(() -> new NotFoundException("Field " + request.fieldId() + " not found"));

        // 3. Si alguna reserva de ese día se cruza con el rango pedido → 409 Conflict.
        boolean taken = reservations.findByFieldIdAndDate(field.getId(), request.date()).stream()
                .anyMatch(r -> r.overlaps(request.startHour(), request.endHour()));   // Experto
        if (taken) {
            throw new ConflictException("That time slot is already booked");
        }

        // 4. La cancha crea la reserva (valida horario y calcula total y anticipo).
        Reservation reservation = field.book(player, request.date(),                 // Creador
                request.startHour(), request.endHour(), depositPolicy);
        reservations.save(reservation);
        // 5. Se avisa al jugador sin saber si es por correo o por log.
        notifications.reservationCreated(reservation);                               // Indirección
        return ReservationResponse.from(reservation);
    }

    /** "Mis reservas" del jugador, de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public List<ReservationResponse> listForPlayer(Long playerId) {
        return reservations.findByPlayerIdOrderByDateDescStartHourDesc(playerId).stream()
                .map(ReservationResponse::from).toList();
    }

    /** Reservas de todas las canchas de un dueño. */
    @Transactional(readOnly = true)
    public List<ReservationResponse> listForOwner(Long ownerId) {
        return reservations.findByFieldOwnerIdOrderByDateDescStartHourDesc(ownerId).stream()
                .map(ReservationResponse::from).toList();
    }

    /** Detalle de una reserva. Solo la puede ver el jugador que reservó o el dueño de la cancha. */
    @Transactional(readOnly = true)
    public ReservationResponse get(Long userId, Long reservationId) {
        Reservation r = load(reservationId);
        if (!r.belongsTo(userId) && !r.getField().isOwnedBy(userId)) {
            throw new ForbiddenException("You cannot see this reservation");
        }
        return ReservationResponse.from(r);
    }

    /** Cancela una reserva y notifica. Los cambios se guardan solos al terminar la transacción. */
    @Transactional
    public ReservationResponse cancel(Long playerId, Long reservationId) {
        Reservation reservation = load(reservationId);
        reservation.cancelBy(playerId);        // Experto: la reserva valida sus propias reglas
        notifications.reservationCancelled(reservation);
        return ReservationResponse.from(reservation);
    }

    /** Busca la reserva o lanza 404. */
    private Reservation load(Long id) {
        return reservations.findById(id).orElseThrow(() -> new NotFoundException("Reservation " + id + " not found"));
    }
}
