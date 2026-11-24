package com.canchaya.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Registro de un intento de pago del anticipo de una reserva (aprobado o rechazado).
 * Solo lo crea {@link Reservation} (GRASP Creador), por eso el constructor no es público.
 */
@Entity
@Table(name = "payments")
public class Payment {

    /** Llave primaria generada por la base de datos. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Reserva a la que pertenece este pago. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    /** Monto pagado (el anticipo de la reserva). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Medio usado: WOMPI, NEQUI o DAVIPLATA (se guarda como texto). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethodType method;

    /** Resultado: APPROVED o DECLINED. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    /** Referencia o id de la transacción en la pasarela, para rastrear el pago. */
    @Column(length = 120)
    private String transactionRef;

    /** Fecha y hora en que se registró el pago. */
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** Constructor vacío obligatorio para JPA. */
    protected Payment() {
        // requerido por JPA
    }

    /** Visibilidad de paquete: solo Reservation (mismo paquete) puede crear pagos. */
    Payment(Reservation reservation, BigDecimal amount, PaymentMethodType method,
            PaymentStatus status, String transactionRef) {
        this.reservation = reservation;
        this.amount = amount;
        this.method = method;
        this.status = status;
        this.transactionRef = transactionRef;
    }

    /** true si la pasarela aprobó el pago. */
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
