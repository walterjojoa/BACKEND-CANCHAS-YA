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
 * GRASP Controller for the "book a field" use case: it receives the system events
 * (reserve, pay, cancel) and delegates. No rules, no SQL, no Wompi here.
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

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                      @Valid @RequestBody CreateReservationRequest request) {
        return reservationService.create(user.id(), request);
    }

    @GetMapping("/reservations/me")
    public List<ReservationResponse> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return reservationService.listForPlayer(user.id());
    }

    @GetMapping("/reservations/{id}")
    public ReservationResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return reservationService.get(user.id(), id);
    }

    @PostMapping("/reservations/{id}/payments")
    public PaymentResponse pay(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                               @Valid @RequestBody PaymentRequest request) {
        return paymentService.payDeposit(user.id(), id, request.method());
    }

    /** Step 1 of a Wompi payment: signed data to open Wompi's checkout. */
    @PostMapping("/reservations/{id}/payments/wompi")
    public WompiCheckoutResponse startWompi(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return wompiCheckout.start(user.id(), id);
    }

    /** Step 2: the player is back from Wompi; record the real result of the transaction. */
    @PostMapping("/payments/wompi/confirm")
    public WompiResultResponse confirmWompi(@AuthenticationPrincipal AuthenticatedUser user,
                                            @Valid @RequestBody WompiConfirmRequest request) {
        return wompiCheckout.confirm(user.id(), request.transactionId());
    }

    /** Webhook Wompi calls on every transaction update (configured in Wompi's dashboard). */
    @PostMapping("/payments/wompi/events")
    public ResponseEntity<Void> wompiEvent(@RequestBody JsonNode event) {
        return wompiCheckout.handleEvent(event) ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @PostMapping("/reservations/{id}/cancel")
    public ReservationResponse cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return reservationService.cancel(user.id(), id);
    }

    @GetMapping("/owner/reservations")
    public List<ReservationResponse> ownerReservations(@AuthenticationPrincipal AuthenticatedUser user) {
        return reservationService.listForOwner(user.id());
    }
}
