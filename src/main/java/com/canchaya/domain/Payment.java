package com.canchaya.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Record of a payment attempt for a reservation's deposit. Created only by {@link Reservation}. */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethodType method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(length = 120)
    private String transactionRef;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Payment() {
        // required by JPA
    }

    Payment(Reservation reservation, BigDecimal amount, PaymentMethodType method,
            PaymentStatus status, String transactionRef) {
        this.reservation = reservation;
        this.amount = amount;
        this.method = method;
        this.status = status;
        this.transactionRef = transactionRef;
    }

    public boolean isApproved() {
        return status == PaymentStatus.APPROVED;
    }

    public Long getId() { return id; }
    public Reservation getReservation() { return reservation; }
    public BigDecimal getAmount() { return amount; }
    public PaymentMethodType getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public String getTransactionRef() { return transactionRef; }
    public Instant getCreatedAt() { return createdAt; }
}
