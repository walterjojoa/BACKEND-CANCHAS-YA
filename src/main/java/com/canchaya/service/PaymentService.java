package com.canchaya.service;

import com.canchaya.domain.Payment;
import com.canchaya.domain.PaymentMethodType;
import com.canchaya.domain.Reservation;
import com.canchaya.dto.ReservationDtos.PaymentResponse;
import com.canchaya.exception.ForbiddenException;
import com.canchaya.exception.NotFoundException;
import com.canchaya.notification.NotificationService;
import com.canchaya.payment.PaymentMethod;
import com.canchaya.payment.PaymentMethodResolver;
import com.canchaya.payment.PaymentResult;
import com.canchaya.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pays the deposit of a reservation.
 * <p>
 * GRASP Indirection: this service stands between the reservation and the concrete payment
 * providers. It asks the resolver for "a method of type X" and sends it the polymorphic
 * message {@code charge(...)}; it never knows whether Wompi, Nequi or Daviplata answers.
 * GRASP High Cohesion: it only handles money; booking rules live in {@link ReservationService}.
 */
@Service
public class PaymentService {

    private final ReservationRepository reservations;
    private final PaymentMethodResolver resolver;
    private final NotificationService notifications;

    public PaymentService(ReservationRepository reservations, PaymentMethodResolver resolver,
                          NotificationService notifications) {
        this.reservations = reservations;
        this.resolver = resolver;
        this.notifications = notifications;
    }

    @Transactional
    public PaymentResponse payDeposit(Long playerId, Long reservationId, PaymentMethodType type) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation " + reservationId + " not found"));
        if (!reservation.belongsTo(playerId)) {
            throw new ForbiddenException("You can only pay your own reservations");
        }

        PaymentMethod method = resolver.resolve(type);                                       // Polymorphism
        PaymentResult result = method.charge(reservation.getDepositAmount(), "RES-" + reservation.getId());

        Payment payment = reservation.registerPayment(type, result.status(), result.transactionRef()); // Creator
        // The reservation is already managed: flush() persists the new payment on this same
        // instance (save() would merge and hand back a copy, leaving payment.getId() null).
        reservations.flush();
        if (payment.isApproved()) {
            notifications.reservationConfirmed(reservation, payment);
        }
        return PaymentResponse.from(payment);
    }
}
