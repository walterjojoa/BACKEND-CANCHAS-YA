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

/** A player's booking of a field for a range of whole hours on a given date. */
@Entity
@Table(name = "reservations", indexes = @Index(columnList = "field_id, date"))
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_id")
    private Field field;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id")
    private User player;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private int startHour;

    @Column(nullable = false)
    private int endHour;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal depositAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.PENDING_PAYMENT;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Payment> payments = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Reservation() {
        // required by JPA
    }

    /** Package-private: only {@link Field#book} creates reservations (GRASP Creator). */
    Reservation(Field field, User player, LocalDate date, int startHour, int endHour,
                BigDecimal totalAmount, BigDecimal depositAmount) {
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
    // GRASP Information Expert: the reservation knows its own hours and
    // status, so it calculates its duration and detects overlaps.
    // ------------------------------------------------------------------
    public int durationHours() {
        return endHour - startHour;
    }

    public boolean overlaps(int otherStart, int otherEnd) {
        return status != ReservationStatus.CANCELLED && startHour < otherEnd && otherStart < endHour;
    }

    public boolean occupies(int hour) {
        return overlaps(hour, hour + 1);
    }

    public boolean belongsTo(Long userId) {
        return player.getId().equals(userId);
    }

    public BigDecimal remainingAmount() {
        return totalAmount.subtract(depositAmount);
    }

    // ------------------------------------------------------------------
    // GRASP Creator: a Reservation records its payments, so it creates them.
    // ------------------------------------------------------------------
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

    public void cancelBy(Long userId) {
        if (!belongsTo(userId)) {
            throw new ForbiddenException("Only the player who booked can cancel this reservation");
        }
        if (status == ReservationStatus.CANCELLED) {
            throw new BusinessException("Reservation is already cancelled");
        }
        status = ReservationStatus.CANCELLED;
    }

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
