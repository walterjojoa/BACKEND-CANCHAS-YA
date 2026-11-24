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
 * Paga el anticipo de una reserva con Nequi o Daviplata (simulados).
 * <p>
 * GRASP Indirección: este servicio se pone en medio de la reserva y los proveedores de pago
 * concretos. Le pide al resolver "un medio de tipo X" y le envía el mensaje polimórfico
 * {@code charge(...)}; nunca sabe si responde Wompi, Nequi o Daviplata.
 * GRASP Alta Cohesión: solo maneja dinero; las reglas de reservar están en {@link ReservationService}.
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

    /**
     * Cobra el anticipo de la reserva con el medio elegido y registra el resultado.
     * Si el pago se aprueba, la reserva queda CONFIRMED y se notifica al jugador.
     */
    @Transactional
    public PaymentResponse payDeposit(Long playerId, Long reservationId, PaymentMethodType type) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation " + reservationId + " not found"));
        // Solo el dueño de la reserva puede pagarla.
        if (!reservation.belongsTo(playerId)) {
            throw new ForbiddenException("You can only pay your own reservations");
        }

        // Polimorfismo: se obtiene el medio de pago y se le pide cobrar, sin importar cuál es.
        PaymentMethod method = resolver.resolve(type);                                       // Polimorfismo
        PaymentResult result = method.charge(reservation.getDepositAmount(), "RES-" + reservation.getId());

        // Creador: la reserva crea y guarda el registro del pago (y cambia su estado si se aprobó).
        Payment payment = reservation.registerPayment(type, result.status(), result.transactionRef()); // Creador
        // La reserva ya está administrada por JPA: flush() guarda el pago nuevo en esta misma
        // instancia (save() haría un merge y devolvería una copia, dejando payment.getId() en null).
        reservations.flush();
        if (payment.isApproved()) {
            notifications.reservationConfirmed(reservation, payment);
        }
        return PaymentResponse.from(payment);
    }
}
