package com.canchaya.service;

import com.canchaya.domain.ReservationStatus;
import com.canchaya.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Libera los turnos de las reservas que nadie pagó a tiempo.
 * <p>
 * Toda reserva nace PENDING_PAYMENT y ocupa su franja. Si el anticipo no se paga en
 * canchaya.reservations.payment-timeout-minutes (15 por defecto), la reserva pasa a CANCELLED
 * y la franja queda libre. Así nadie puede bloquear una cancha entera sin pagar.
 * <p>
 * Se usa CANCELLED (y no un estado nuevo) porque la base de datos solo acepta
 * PENDING_PAYMENT, CONFIRMED y CANCELLED, y su restricción anti-cruces (V07) solo ignora
 * las canceladas.
 * <p>
 * GRASP Fabricación Pura + Alta Cohesión: el vencimiento es una sola responsabilidad,
 * separada de crear reservas ({@link ReservationService}) y de cobrarlas ({@link PaymentService}).
 */
@Service
public class ReservationExpiryService {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryService.class);

    private final ReservationRepository reservations;
    private final Clock clock;
    private final Duration timeout;

    public ReservationExpiryService(ReservationRepository reservations, Clock clock,
                                    @Value("${canchaya.reservations.payment-timeout-minutes:15}") long timeoutMinutes) {
        this.reservations = reservations;
        this.clock = clock;
        this.timeout = Duration.ofMinutes(timeoutMinutes);
    }

    /** Las reservas pendientes creadas antes de este instante ya vencieron. */
    public Instant deadline() {
        return Instant.now(clock).minus(timeout);
    }

    /**
     * Cancela en la base de datos las reservas vencidas de una cancha en un día. Se llama antes de
     * revisar cruces al crear una reserva, para que el turno quede libre aunque el job aún no haya corrido.
     * Es un UPDATE directo y condicionado (status = PENDING_PAYMENT), así que nunca pisa un pago confirmado.
     */
    @Transactional
    public void releaseExpired(Long fieldId, LocalDate date) {
        reservations.releaseUnpaid(fieldId, date, deadline(),
                ReservationStatus.PENDING_PAYMENT, ReservationStatus.CANCELLED);
    }

    /** Job que corre cada minuto y libera todas las reservas vencidas (para que "Mis reservas" se vea al día). */
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    @Transactional
    public void releaseAllExpired() {
        int released = reservations.releaseAllUnpaid(deadline(),
                ReservationStatus.PENDING_PAYMENT, ReservationStatus.CANCELLED);
        if (released > 0) {
            log.info("Released {} unpaid reservation(s) older than {}", released, timeout);
        }
    }
}
