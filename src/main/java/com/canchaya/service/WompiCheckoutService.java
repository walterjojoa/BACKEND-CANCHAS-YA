package com.canchaya.service;

import com.canchaya.domain.Payment;
import com.canchaya.domain.PaymentMethodType;
import com.canchaya.domain.PaymentStatus;
import com.canchaya.domain.Reservation;
import com.canchaya.domain.ReservationStatus;
import com.canchaya.dto.ReservationDtos.WompiCheckoutResponse;
import com.canchaya.dto.ReservationDtos.WompiResultResponse;
import com.canchaya.exception.BusinessException;
import com.canchaya.exception.ForbiddenException;
import com.canchaya.exception.NotFoundException;
import com.canchaya.notification.NotificationService;
import com.canchaya.payment.WompiClient;
import com.canchaya.payment.WompiTransaction;
import com.canchaya.repository.ReservationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Paga el anticipo con el Web Checkout de Wompi (tarjeta, PSE, Nequi, botón Bancolombia).
 * <ol>
 *   <li>{@link #start}: firma el monto para que el navegador del jugador abra el checkout de Wompi.</li>
 *   <li>El jugador paga en la página de Wompi y Wompi lo devuelve a CanchaYa con el id de la transacción.</li>
 *   <li>{@link #confirm} (desde el frontend) o {@link #handleEvent} (webhook de Wompi) le preguntan a
 *       Wompi el resultado real y lo registran. Ambos son idempotentes: el que llegue primero gana
 *       y el segundo no duplica el pago.</li>
 * </ol>
 * GRASP Indirección: la reserva nunca habla con Wompi; este servicio traduce entre nuestro
 * negocio (reserva, pesos) y la API de Wompi (referencia, centavos, firmas).
 */
@Service
public class WompiCheckoutService {

    /** Moneda: pesos colombianos. */
    static final String CURRENCY = "COP";
    /** Formato de la referencia que enviamos a Wompi: RES-{idReserva}-{marcaDeTiempo}. */
    private static final Pattern REFERENCE = Pattern.compile("^RES-(\\d+)-\\d+$");

    private final ReservationRepository reservations;
    private final WompiClient wompi;
    private final NotificationService notifications;

    public WompiCheckoutService(ReservationRepository reservations, WompiClient wompi,
                                NotificationService notifications) {
        this.reservations = reservations;
        this.wompi = wompi;
        this.notifications = notifications;
    }

    /**
     * Paso 1: prepara los datos para abrir el checkout de Wompi (llave pública, referencia,
     * monto en centavos y firma de integridad). No guarda nada en la BD.
     */
    @Transactional(readOnly = true)
    public WompiCheckoutResponse start(Long playerId, Long reservationId) {
        // Sin llaves configuradas en el servidor no se puede usar Wompi.
        if (!wompi.isConfigured()) {
            throw new BusinessException("Wompi is not configured on the server");
        }
        Reservation reservation = ownReservation(playerId, reservationId);
        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            throw new BusinessException("Reservation " + reservationId + " is " + reservation.getStatus() + " and cannot be paid");
        }
        // Una referencia nueva en cada intento: Wompi rechaza una referencia ya usada.
        String reference = "RES-" + reservation.getId() + "-" + System.currentTimeMillis();
        long cents = toCents(reservation.getDepositAmount());
        // La firma evita que alguien cambie el monto en el navegador antes de pagar.
        return new WompiCheckoutResponse(reservation.getId(), wompi.publicKey(), reference, cents, CURRENCY,
                wompi.integritySignature(reference, cents, CURRENCY));
    }

    /** Paso 3a: lo llama el frontend cuando Wompi devuelve al jugador con ?id=idTransaccion. */
    @Transactional
    public WompiResultResponse confirm(Long playerId, String transactionId) {
        // Se consulta la transacción directamente a Wompi (no se confía en lo que diga el navegador).
        WompiTransaction tx = wompi.findTransaction(transactionId)
                .orElseThrow(() -> new NotFoundException("Wompi transaction " + transactionId + " not found"));
        Reservation reservation = reservationOf(tx);
        if (!reservation.belongsTo(playerId)) {
            throw new ForbiddenException("You can only pay your own reservations");
        }
        return record(reservation, tx);
    }

    /**
     * Paso 3b: lo llama el webhook de Wompi (evento transaction.updated).
     * Devuelve false si el evento no es auténtico (checksum inválido).
     */
    @Transactional
    public boolean handleEvent(JsonNode event) {
        if (!wompi.isAuthentic(event)) {
            return false;
        }
        String transactionId = event.path("data").path("transaction").path("id").asText();
        // Se vuelve a preguntar a Wompi en vez de confiar en el cuerpo: la API es la fuente de verdad.
        wompi.findTransaction(transactionId).ifPresent(tx -> record(reservationOf(tx), tx));
        return true;
    }

    /**
     * Registra el resultado de una transacción en la reserva, de forma idempotente
     * (si ya se registró, no lo vuelve a hacer).
     */
    private WompiResultResponse record(Reservation reservation, WompiTransaction tx) {
        // Seguridad: el monto y la moneda pagados deben coincidir exactamente con el anticipo.
        if (tx.amountInCents() != toCents(reservation.getDepositAmount()) || !CURRENCY.equals(tx.currency())) {
            throw new BusinessException("Wompi transaction " + tx.id() + " does not match the deposit");
        }
        Optional<Payment> existing = reservation.getPayments().stream()
                .filter(p -> tx.id().equals(p.getTransactionRef())).findFirst();
        if (existing.isPresent()) {                                   // ya registrado: idempotente
            return result(reservation, tx, existing.get().getStatus().name());
        }
        // El banco todavía no responde: no se registra nada aún.
        if (tx.isPending()) {
            return result(reservation, tx, "PENDING");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            // p. ej. se canceló mientras el jugador pagaba: aquí no hay nada que registrar
            return result(reservation, tx, tx.isApproved() ? "APPROVED" : "DECLINED");
        }
        PaymentStatus status = tx.isApproved() ? PaymentStatus.APPROVED : PaymentStatus.DECLINED;
        Payment payment = reservation.registerPayment(PaymentMethodType.WOMPI, status, tx.id()); // Creador
        reservations.flush();
        if (payment.isApproved()) {
            notifications.reservationConfirmed(reservation, payment);
        }
        return result(reservation, tx, status.name());
    }

    /** Arma la respuesta que ve el frontend. */
    private static WompiResultResponse result(Reservation reservation, WompiTransaction tx, String status) {
        return new WompiResultResponse(reservation.getId(), tx.id(), status, tx.paymentMethodType(),
                reservation.getStatus());
    }

    /** Busca la reserva y verifica que sea del jugador (404 / 403). */
    private Reservation ownReservation(Long playerId, Long reservationId) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation " + reservationId + " not found"));
        if (!reservation.belongsTo(playerId)) {
            throw new ForbiddenException("You can only pay your own reservations");
        }
        return reservation;
    }

    /** Saca el id de la reserva de la referencia "RES-{id}-{tiempo}" de la transacción. */
    private Reservation reservationOf(WompiTransaction tx) {
        Matcher m = REFERENCE.matcher(tx.reference());
        if (!m.matches()) {
            throw new BusinessException("Wompi transaction " + tx.id() + " is not a CanchaYa payment");
        }
        Long id = Long.valueOf(m.group(1));
        return reservations.findById(id).orElseThrow(() -> new NotFoundException("Reservation " + id + " not found"));
    }

    /** Wompi trabaja en centavos: 24000 pesos → 2400000 centavos. */
    static long toCents(BigDecimal pesos) {
        return pesos.movePointRight(2).longValueExact();
    }
}
