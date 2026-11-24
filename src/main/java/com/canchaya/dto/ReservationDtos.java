package com.canchaya.dto;

import com.canchaya.domain.Payment;
import com.canchaya.domain.PaymentMethodType;
import com.canchaya.domain.PaymentStatus;
import com.canchaya.domain.Reservation;
import com.canchaya.domain.ReservationStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** DTOs de /api/reservations y de los pagos: lo que entra y sale en JSON. */
public final class ReservationDtos {

    private ReservationDtos() {
    }

    /** Cuerpo para crear una reserva. La fecha no puede ser pasada (@FutureOrPresent). */
    public record CreateReservationRequest(
            @NotNull Long fieldId,
            @NotNull @FutureOrPresent LocalDate date,
            @Min(0) @Max(23) int startHour,
            @Min(1) @Max(24) int endHour) {
    }

    /** Reserva tal como la ve el frontend, con el total, el anticipo y lo que falta pagar. */
    public record ReservationResponse(Long id, Long fieldId, String fieldName, String playerName,
                                      LocalDate date, int startHour, int endHour,
                                      BigDecimal totalAmount, BigDecimal depositAmount,
                                      BigDecimal remainingAmount, ReservationStatus status) {
        /** Convierte la entidad Reservation en DTO. */
        public static ReservationResponse from(Reservation r) {
            return new ReservationResponse(r.getId(), r.getField().getId(), r.getField().getName(),
                    r.getPlayer().getName(), r.getDate(), r.getStartHour(), r.getEndHour(),
                    r.getTotalAmount(), r.getDepositAmount(), r.remainingAmount(), r.getStatus());
        }
    }

    /** Cuerpo para pagar el anticipo: el medio (NEQUI o DAVIPLATA). */
    public record PaymentRequest(@NotNull PaymentMethodType method) {
    }

    /** Resultado del pago, incluyendo el nuevo estado de la reserva. */
    public record PaymentResponse(Long paymentId, Long reservationId, BigDecimal amount,
                                  PaymentMethodType method, PaymentStatus status,
                                  String transactionRef, ReservationStatus reservationStatus) {
        /** Convierte la entidad Payment en DTO. */
        public static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getReservation().getId(), p.getAmount(), p.getMethod(),
                    p.getStatus(), p.getTransactionRef(), p.getReservation().getStatus());
        }
    }

    /** Datos que necesita el navegador para abrir el Web Checkout de Wompi. */
    public record WompiCheckoutResponse(Long reservationId, String publicKey, String reference,
                                        long amountInCents, String currency, String integritySignature) {
    }

    /** Cuerpo para confirmar un pago de Wompi: el id de la transacción que devolvió Wompi. */
    public record WompiConfirmRequest(@NotBlank String transactionId) {
    }

    /** status: APPROVED, DECLINED o PENDING (el banco todavía la está procesando). */
    public record WompiResultResponse(Long reservationId, String transactionId, String status,
                                      String paymentMethodType, ReservationStatus reservationStatus) {
    }
}
