package com.canchaya.notification;

import com.canchaya.domain.Payment;
import com.canchaya.domain.Reservation;
import org.springframework.stereotype.Service;

/**
 * Redacta los mensajes que reciben los usuarios.
 * <p>
 * GRASP Alta Cohesión: la única tarea de esta clase es escribir esos mensajes.
 * GRASP Indirección: los servicios de reservas y pagos solo piden "avisa que pasó X";
 * nunca saben qué {@link NotificationSender} lo entrega (log o correo).
 */
@Service
public class NotificationService {

    /** Spring inyecta el emisor activo (LogNotificationSender o SmtpNotificationSender). */
    private final NotificationSender sender;

    public NotificationService(NotificationSender sender) {
        this.sender = sender;
    }

    /** Aviso al jugador: reserva creada, falta pagar el anticipo. */
    public void reservationCreated(Reservation r) {
        sender.send(r.getPlayer().getEmail(),
                "CanchaYa - Reservation #" + r.getId() + " created",
                "You booked " + r.getField().getName() + " on " + r.getDate() + " from "
                        + r.getStartHour() + ":00 to " + r.getEndHour() + ":00. "
                        + "Pay the deposit of $" + r.getDepositAmount() + " to confirm it.");
    }

    /** Aviso al jugador Y al dueño: reserva confirmada, con el saldo que falta pagar en la cancha. */
    public void reservationConfirmed(Reservation r, Payment payment) {
        String body = "Your reservation at " + r.getField().getName() + " on " + r.getDate()
                + " is confirmed. Deposit paid: $" + payment.getAmount()
                + " (" + payment.getMethod() + ", ref " + payment.getTransactionRef() + "). "
                + "Pay $" + r.remainingAmount() + " at the field.";
        sender.send(r.getPlayer().getEmail(), "CanchaYa - Reservation #" + r.getId() + " confirmed", body);
        sender.send(r.getField().getOwner().getEmail(), "CanchaYa - New confirmed reservation", body);
    }

    /** Aviso al jugador Y al dueño: reserva cancelada (el turno queda libre). */
    public void reservationCancelled(Reservation r) {
        String body = "Reservation #" + r.getId() + " at " + r.getField().getName() + " on "
                + r.getDate() + " " + r.getStartHour() + ":00 was cancelled.";
        sender.send(r.getPlayer().getEmail(), "CanchaYa - Reservation cancelled", body);
        sender.send(r.getField().getOwner().getEmail(), "CanchaYa - Reservation cancelled", body);
    }
}
