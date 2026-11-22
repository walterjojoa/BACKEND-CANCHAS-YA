package com.canchaya.notification;

import com.canchaya.domain.Payment;
import com.canchaya.domain.Reservation;
import org.springframework.stereotype.Service;

/**
 * GRASP High Cohesion: the only job of this class is to write the messages users receive.
 * GRASP Indirection: reservation and payment services ask for "notify that X happened";
 * they never know which {@link NotificationSender} delivers it.
 */
@Service
public class NotificationService {

    private final NotificationSender sender;

    public NotificationService(NotificationSender sender) {
        this.sender = sender;
    }

    public void reservationCreated(Reservation r) {
        sender.send(r.getPlayer().getEmail(),
                "CanchaYa - Reservation #" + r.getId() + " created",
                "You booked " + r.getField().getName() + " on " + r.getDate() + " from "
                        + r.getStartHour() + ":00 to " + r.getEndHour() + ":00. "
                        + "Pay the deposit of $" + r.getDepositAmount() + " to confirm it.");
    }

    public void reservationConfirmed(Reservation r, Payment payment) {
        String body = "Your reservation at " + r.getField().getName() + " on " + r.getDate()
                + " is confirmed. Deposit paid: $" + payment.getAmount()
                + " (" + payment.getMethod() + ", ref " + payment.getTransactionRef() + "). "
                + "Pay $" + r.remainingAmount() + " at the field.";
        sender.send(r.getPlayer().getEmail(), "CanchaYa - Reservation #" + r.getId() + " confirmed", body);
        sender.send(r.getField().getOwner().getEmail(), "CanchaYa - New confirmed reservation", body);
    }

    public void reservationCancelled(Reservation r) {
        String body = "Reservation #" + r.getId() + " at " + r.getField().getName() + " on "
                + r.getDate() + " " + r.getStartHour() + ":00 was cancelled.";
        sender.send(r.getPlayer().getEmail(), "CanchaYa - Reservation cancelled", body);
        sender.send(r.getField().getOwner().getEmail(), "CanchaYa - Reservation cancelled", body);
    }
}
