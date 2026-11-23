package com.canchaya.dto;

import com.canchaya.domain.Payment;
import com.canchaya.domain.PaymentMethodType;
import com.canchaya.domain.PaymentStatus;
import com.canchaya.domain.Reservation;
import com.canchaya.domain.ReservationStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Request/response bodies for /api/reservations. */
public final class ReservationDtos {

    private ReservationDtos() {
    }

    public record CreateReservationRequest(
            @NotNull Long fieldId,
            @NotNull @FutureOrPresent LocalDate date,
            @Min(0) @Max(23) int startHour,
            @Min(1) @Max(24) int endHour) {
    }

    public record ReservationResponse(Long id, Long fieldId, String fieldName, String playerName,
                                      LocalDate date, int startHour, int endHour,
                                      BigDecimal totalAmount, BigDecimal depositAmount,
                                      BigDecimal remainingAmount, ReservationStatus status) {
        public static ReservationResponse from(Reservation r) {
            return new ReservationResponse(r.getId(), r.getField().getId(), r.getField().getName(),
                    r.getPlayer().getName(), r.getDate(), r.getStartHour(), r.getEndHour(),
                    r.getTotalAmount(), r.getDepositAmount(), r.remainingAmount(), r.getStatus());
        }
    }

    public record PaymentRequest(@NotNull PaymentMethodType method) {
    }

    public record PaymentResponse(Long paymentId, Long reservationId, BigDecimal amount,
                                  PaymentMethodType method, PaymentStatus status,
                                  String transactionRef, ReservationStatus reservationStatus) {
        public static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getReservation().getId(), p.getAmount(), p.getMethod(),
                    p.getStatus(), p.getTransactionRef(), p.getReservation().getStatus());
        }
    }

    /** Data the browser needs to open Wompi's Web Checkout. */
    public record WompiCheckoutResponse(Long reservationId, String publicKey, String reference,
                                        long amountInCents, String currency, String integritySignature) {
    }

    public record WompiConfirmRequest(@NotBlank String transactionId) {
    }

    /** status: APPROVED, DECLINED or PENDING (still being processed by the bank). */
    public record WompiResultResponse(Long reservationId, String transactionId, String status,
                                      String paymentMethodType, ReservationStatus reservationStatus) {
    }
}
