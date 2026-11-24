package com.canchaya.web;

import com.canchaya.dto.ReservationDtos.CreateReservationRequest;
import com.canchaya.dto.ReservationDtos.PaymentRequest;
import com.canchaya.dto.ReservationDtos.PaymentResponse;
import com.canchaya.dto.ReservationDtos.ReservationResponse;
import com.canchaya.dto.ReservationDtos.WompiCheckoutResponse;
import com.canchaya.dto.ReservationDtos.WompiConfirmRequest;
import com.canchaya.dto.ReservationDtos.WompiResultResponse;
import com.canchaya.security.AuthenticatedUser;
import com.canchaya.service.PaymentService;
import com.canchaya.service.ReservationService;
import com.canchaya.service.WompiCheckoutService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de reservas y pagos.
 * GRASP Controlador para el caso de uso "reservar una cancha": recibe los eventos del sistema
 * (reservar, pagar, cancelar) y delega. Aquí no hay reglas, ni SQL, ni Wompi.
 */
@RestController
@RequestMapping("/api")
public class ReservationController {

    private final ReservationService reservationService;
    private final PaymentService paymentService;
    private final WompiCheckoutService wompiCheckout;

    public ReservationController(ReservationService reservationService, PaymentService paymentService,
                                 WompiCheckoutService wompiCheckout) {
        this.reservationService = reservationService;
        this.paymentService = paymentService;
        this.wompiCheckout = wompiCheckout;
    }

    /** POST /api/reservations : crea una reserva (solo PLAYER, 201 Created, 409 si la hora está ocupada). */
    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                      @Valid @RequestBody CreateReservationRequest request) {
        return reservationService.create(user.id(), request);
    }

    /** GET /api/reservations/me : reservas del jugador con la sesión iniciada. */
    @GetMapping("/reservations/me")
    public List<ReservationResponse> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return reservationService.listForPlayer(user.id());
    }

    /** GET /api/reservations/{id} : detalle (solo el jugador que reservó o el dueño de la cancha). */
    @GetMapping("/reservations/{id}")
    public ReservationResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return reservationService.get(user.id(), id);
    }

    /** POST /api/reservations/{id}/payments : paga el anticipo con NEQUI o DAVIPLATA (simulados). */
    @PostMapping("/reservations/{id}/payments")
    public PaymentResponse pay(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                               @Valid @RequestBody PaymentRequest request) {
        return paymentService.payDeposit(user.id(), id, request.method());
    }

    /** Paso 1 de un pago con Wompi: datos firmados para abrir el checkout de Wompi. */
    @PostMapping("/reservations/{id}/payments/wompi")
    public WompiCheckoutResponse startWompi(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return wompiCheckout.start(user.id(), id);
    }

    /** Paso 2: el jugador volvió de Wompi; se registra el resultado real de la transacción. */
    @PostMapping("/payments/wompi/confirm")
    public WompiResultResponse confirmWompi(@AuthenticationPrincipal AuthenticatedUser user,
                                            @Valid @RequestBody WompiConfirmRequest request) {
        return wompiCheckout.confirm(user.id(), request.transactionId());
    }

    /**
     * Webhook que Wompi llama en cada cambio de una transacción (se configura en el panel de Wompi).
     * Es público pero seguro: si el checksum no es válido responde 401.
     */
    @PostMapping("/payments/wompi/events")
    public ResponseEntity<Void> wompiEvent(@RequestBody JsonNode event) {
        return wompiCheckout.handleEvent(event) ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    /** POST /api/reservations/{id}/cancel : cancela la reserva (solo quien la hizo). */
    @PostMapping("/reservations/{id}/cancel")
    public ReservationResponse cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return reservationService.cancel(user.id(), id);
    }

    /** GET /api/owner/reservations : reservas de todas las canchas del dueño (solo OWNER). */
    @GetMapping("/owner/reservations")
    public List<ReservationResponse> ownerReservations(@AuthenticationPrincipal AuthenticatedUser user) {
        return reservationService.listForOwner(user.id());
    }
}
