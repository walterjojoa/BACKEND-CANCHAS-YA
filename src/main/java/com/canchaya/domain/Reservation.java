package com.canchaya.domain;

import com.canchaya.exception.BusinessException;
import com.canchaya.exception.ForbiddenException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reserva de una cancha hecha por un jugador, para un rango de horas completas en una fecha.
 *
 * <p>Ciclo de vida: PENDING_PAYMENT (creada) → CONFIRMED (anticipo pagado) o CANCELLED.
 */
@Entity
// Índice por (cancha, fecha) porque es la consulta más frecuente: "reservas de esta cancha este día".
@Table(name = "reservations", indexes = @Index(columnList = "field_id, date"))
public class Reservation {

    /** Llave primaria generada por la base de datos. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Cancha reservada. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_id")
    private Field field;

    /** Jugador que hizo la reserva. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id")
    private User player;

    /** Día de la reserva. */
    @Column(nullable = false)
    private LocalDate date;

    /** Hora de inicio (0-23) y de fin (1-24). Ej.: 18 a 20 = dos horas. */
    @Column(nullable = false)
    private int startHour;

    @Column(nullable = false)
    private int endHour;

    /** Valor total a pagar por las horas reservadas. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    /** Anticipo que se paga en línea para asegurar el turno (p. ej. 30 % del total). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal depositAmount;

    /** Estado actual; toda reserva nace pendiente de pago. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.PENDING_PAYMENT;

    /** Historial de intentos de pago de esta reserva. */
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Payment> payments = new ArrayList<>();

    /** Fecha y hora de creación. */
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** Constructor vacío obligatorio para JPA. */
    protected Reservation() {
        // requerido por JPA
    }

    /** Visibilidad de paquete: solo {@link Field#book} crea reservas (GRASP Creador). */
    Reservation(Field field, User player, LocalDate date, int startHour, int endHour,
                BigDecimal totalAmount, BigDecimal depositAmount) {
        // Validación de horas: dentro del día y el fin después del inicio.
        if (startHour < 0 || endHour > 24 || endHour <= startHour) {
            throw new BusinessException("Invalid hours: " + startHour + "-" + endHour);
        }
        this.field = field;
        this.player = player;
        this.date = date;
        this.startHour = startHour;
        this.endHour = endHour;
        this.totalAmount = totalAmount;
        this.depositAmount = depositAmount;
    }

    // ------------------------------------------------------------------
    // GRASP Experto en Información: la reserva conoce sus horas y su estado,
    // así que ella calcula su duración y detecta si se cruza con otra.
    // ------------------------------------------------------------------

    /** Cantidad de horas reservadas. */
    public int durationHours() {
        return endHour - startHour;
    }

    /**
     * true si esta reserva se cruza con el rango [otherStart, otherEnd).
     * Las reservas canceladas nunca se cruzan (liberan el turno).
     * Dos rangos se cruzan si cada uno empieza antes de que el otro termine.
     */
    public boolean overlaps(int otherStart, int otherEnd) {
        return status != ReservationStatus.CANCELLED && startHour < otherEnd && otherStart < endHour;
    }

    /** true si esta reserva ocupa la franja que empieza a esa hora (se usa para la disponibilidad). */
    public boolean occupies(int hour) {
        return overlaps(hour, hour + 1);
    }

    /**
     * true si sigue sin pagar y se creó antes de deadline: su turno ya venció y no debe
     * contar como ocupado (ver {@link com.canchaya.service.ReservationExpiryService}).
     */
    public boolean isUnpaidSince(Instant deadline) {
        return status == ReservationStatus.PENDING_PAYMENT && createdAt.isBefore(deadline);
    }

    /** true si la reserva es del usuario con ese id. */
    public boolean belongsTo(Long userId) {
        return player.getId().equals(userId);
    }

    /** Lo que falta pagar en la cancha = total − anticipo. */
    public BigDecimal remainingAmount() {
        return totalAmount.subtract(depositAmount);
    }

    // ------------------------------------------------------------------
    // GRASP Creador: una reserva registra sus pagos, así que ella los crea.
    // ------------------------------------------------------------------

    /**
     * Registra un intento de pago del anticipo. Si fue aprobado, la reserva pasa a CONFIRMED.
     * Solo se puede pagar una reserva que siga pendiente.
     */
    public Payment registerPayment(PaymentMethodType method, PaymentStatus paymentStatus, String transactionRef) {
        if (status != ReservationStatus.PENDING_PAYMENT) {
            throw new BusinessException("Reservation " + id + " is " + status + " and cannot be paid");
        }
        Payment payment = new Payment(this, depositAmount, method, paymentStatus, transactionRef);
        payments.add(payment);
        if (payment.isApproved()) {
            status = ReservationStatus.CONFIRMED;
        }
        return payment;
    }

    /** Cancela la reserva. Solo el jugador que reservó puede hacerlo, y no se cancela dos veces. */
    public void cancelBy(Long userId) {
        if (!belongsTo(userId)) {
            throw new ForbiddenException("Only the player who booked can cancel this reservation");
        }
        if (status == ReservationStatus.CANCELLED) {
            throw new BusinessException("Reservation is already cancelled");
        }
        status = ReservationStatus.CANCELLED;
    }

    // Getters de solo lectura (sin setters: el estado cambia con los métodos de negocio).
    public Long getId() { return id; }
    public Field getField() { return field; }
    public User getPlayer() { return player; }
    public LocalDate getDate() { return date; }
    public int getStartHour() { return startHour; }
    public int getEndHour() { return endHour; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getDepositAmount() { return depositAmount; }
    public ReservationStatus getStatus() { return status; }
    public List<Payment> getPayments() { return Collections.unmodifiableList(payments); }
    public Instant getCreatedAt() { return createdAt; }
}
