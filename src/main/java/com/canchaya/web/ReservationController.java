package com.canchaya.web;

import com.canchaya.dto.ReservationDtos.CreateReservationRequest;
import com.canchaya.dto.ReservationDtos.PaymentRequest;
import com.canchaya.dto.ReservationDtos.PaymentResponse;
import com.canchaya.dto.ReservationDtos.ReservationResponse;
import com.canchaya.security.AuthenticatedUser;
import com.canchaya.service.PaymentService;
import com.canchaya.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    public ReservationController(ReservationService reservationService, PaymentService paymentService) {
        this.reservationService = reservationService;
        this.paymentService = paymentService;
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

    @PostMapping("/reservations/{id}/cancel")
    public ReservationResponse cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return reservationService.cancel(user.id(), id);
    }

    @GetMapping("/owner/reservations")
    public List<ReservationResponse> ownerReservations(@AuthenticationPrincipal AuthenticatedUser user) {
        return reservationService.listForOwner(user.id());
    }
}
