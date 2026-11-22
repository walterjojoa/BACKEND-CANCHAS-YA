package com.canchaya.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Low-level client for the Wompi API (simulated in this version: no real HTTP call yet).
 * It speaks Wompi's language: amounts in cents, currency code, public key, etc.
 * Only {@link WompiPaymentMethod} talks to it.
 */
@Component
public class WompiClient {

    private final String publicKey;

    public WompiClient(@Value("${canchaya.wompi.public-key:pub_test_canchaya}") String publicKey) {
        this.publicKey = publicKey;
    }

    public String createTransaction(long amountInCents, String currency, String reference) {
        // TODO: replace with a real POST to https://sandbox.wompi.co/v1/transactions using publicKey
        return "WOMPI-" + reference + "-" + amountInCents + currency + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    String publicKey() {
        return publicKey;
    }
}
