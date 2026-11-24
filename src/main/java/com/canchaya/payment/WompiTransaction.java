package com.canchaya.payment;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Los datos que usamos de una transacción de Wompi.
 * status: PENDING, APPROVED, DECLINED, VOIDED o ERROR.
 * paymentMethodType: CARD, PSE, NEQUI, BANCOLOMBIA_TRANSFER, ...
 */
public record WompiTransaction(String id, String reference, long amountInCents, String currency,
                               String status, String paymentMethodType) {

    /** Convierte el JSON de Wompi (snake_case) en este record. */
    static WompiTransaction from(JsonNode data) {
        return new WompiTransaction(
                data.path("id").asText(),
                data.path("reference").asText(),
                data.path("amount_in_cents").asLong(),
                data.path("currency").asText(),
                data.path("status").asText(),
                data.path("payment_method_type").asText(""));
    }

    /** true si el banco aún no ha dado respuesta. */
    public boolean isPending() {
        return "PENDING".equals(status);
    }

    /** true si el pago fue aprobado. */
    public boolean isApproved() {
        return "APPROVED".equals(status);
    }
}
