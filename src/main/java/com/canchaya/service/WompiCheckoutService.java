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
 * Pays a deposit through Wompi's Web Checkout (card, PSE, Nequi, Bancolombia button).
 * <ol>
 *   <li>{@link #start}: signs the amount so the player's browser can open Wompi's checkout.</li>
 *   <li>The player pays on Wompi's page and Wompi sends them back with the transaction id.</li>
 *   <li>{@link #confirm} (frontend) or {@link #handleEvent} (Wompi webhook) asks Wompi for the
 *       real result and records it. Both are idempotent, so whichever arrives first wins.</li>
 * </ol>
 * GRASP Indirection: the reservation never talks to Wompi; this service translates between
 * our business (reservation, pesos) and Wompi's API (reference, cents, signatures).
 */
@Service
public class WompiCheckoutService {

    static final String CURRENCY = "COP";
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

    @Transactional(readOnly = true)
    public WompiCheckoutResponse start(Long playerId, Long reservationId) {
        if (!wompi.isConfigured()) {
            throw new BusinessException("Wompi is not configured on the server");
        }
        Reservation reservation = ownReservation(playerId, reservationId);
        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            throw new BusinessException("Reservation " + reservationId + " is " + reservation.getStatus() + " and cannot be paid");
        }
        // A new reference per attempt: Wompi rejects a reference that was already used.
        String reference = "RES-" + reservation.getId() + "-" + System.currentTimeMillis();
        long cents = toCents(reservation.getDepositAmount());
        return new WompiCheckoutResponse(reservation.getId(), wompi.publicKey(), reference, cents, CURRENCY,
                wompi.integritySignature(reference, cents, CURRENCY));
    }

    /** Called by the frontend when Wompi redirects the player back with ?id=transactionId. */
    @Transactional
    public WompiResultResponse confirm(Long playerId, String transactionId) {
        WompiTransaction tx = wompi.findTransaction(transactionId)
                .orElseThrow(() -> new NotFoundException("Wompi transaction " + transactionId + " not found"));
        Reservation reservation = reservationOf(tx);
        if (!reservation.belongsTo(playerId)) {
            throw new ForbiddenException("You can only pay your own reservations");
        }
        return record(reservation, tx);
    }

    /** Called by Wompi's webhook (transaction.updated). Returns false if the event is not authentic. */
    @Transactional
    public boolean handleEvent(JsonNode event) {
        if (!wompi.isAuthentic(event)) {
            return false;
        }
        String transactionId = event.path("data").path("transaction").path("id").asText();
        // Ask Wompi again instead of trusting the body: the API is the source of truth.
        wompi.findTransaction(transactionId).ifPresent(tx -> record(reservationOf(tx), tx));
        return true;
    }

    private WompiResultResponse record(Reservation reservation, WompiTransaction tx) {
        if (tx.amountInCents() != toCents(reservation.getDepositAmount()) || !CURRENCY.equals(tx.currency())) {
            throw new BusinessException("Wompi transaction " + tx.id() + " does not match the deposit");
        }
        Optional<Payment> existing = reservation.getPayments().stream()
                .filter(p -> tx.id().equals(p.getTransactionRef())).findFirst();
        if (existing.isPresent()) {                                   // already recorded: idempotent
            return result(reservation, tx, existing.get().getStatus().name());
        }
        if (tx.isPending()) {
            return result(reservation, tx, "PENDING");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            // e.g. cancelled while the player was paying: nothing to record here
            return result(reservation, tx, tx.isApproved() ? "APPROVED" : "DECLINED");
        }
        PaymentStatus status = tx.isApproved() ? PaymentStatus.APPROVED : PaymentStatus.DECLINED;
        Payment payment = reservation.registerPayment(PaymentMethodType.WOMPI, status, tx.id()); // Creator
        reservations.flush();
        if (payment.isApproved()) {
            notifications.reservationConfirmed(reservation, payment);
        }
        return result(reservation, tx, status.name());
    }

    private static WompiResultResponse result(Reservation reservation, WompiTransaction tx, String status) {
        return new WompiResultResponse(reservation.getId(), tx.id(), status, tx.paymentMethodType(),
                reservation.getStatus());
    }

    private Reservation ownReservation(Long playerId, Long reservationId) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation " + reservationId + " not found"));
        if (!reservation.belongsTo(playerId)) {
            throw new ForbiddenException("You can only pay your own reservations");
        }
        return reservation;
    }

    private Reservation reservationOf(WompiTransaction tx) {
        Matcher m = REFERENCE.matcher(tx.reference());
        if (!m.matches()) {
            throw new BusinessException("Wompi transaction " + tx.id() + " is not a CanchaYa payment");
        }
        Long id = Long.valueOf(m.group(1));
        return reservations.findById(id).orElseThrow(() -> new NotFoundException("Reservation " + id + " not found"));
    }

    static long toCents(BigDecimal pesos) {
        return pesos.movePointRight(2).longValueExact();
    }
}
