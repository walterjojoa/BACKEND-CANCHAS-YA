package com.canchaya.payment;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The fields we use from a Wompi transaction.
 * status: PENDING, APPROVED, DECLINED, VOIDED or ERROR.
 * paymentMethodType: CARD, PSE, NEQUI, BANCOLOMBIA_TRANSFER, ...
 */
public record WompiTransaction(String id, String reference, long amountInCents, String currency,
                               String status, String paymentMethodType) {

    static WompiTransaction from(JsonNode data) {
        return new WompiTransaction(
                data.path("id").asText(),
                data.path("reference").asText(),
                data.path("amount_in_cents").asLong(),
                data.path("currency").asText(),
                data.path("status").asText(),
                data.path("payment_method_type").asText(""));
    }

    public boolean isPending() {
        return "PENDING".equals(status);
    }

    public boolean isApproved() {
        return "APPROVED".equals(status);
    }
}
