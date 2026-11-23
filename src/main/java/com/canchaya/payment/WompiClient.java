package com.canchaya.payment;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Low-level client for the Wompi API (sandbox by default, so no real money moves).
 * It speaks Wompi's language: amounts in cents, SHA-256 signatures, snake_case JSON.
 * Only {@link com.canchaya.service.WompiCheckoutService} talks to it.
 * <p>
 * Keys come from the environment (WOMPI_PUBLIC_KEY, WOMPI_INTEGRITY_SECRET,
 * WOMPI_EVENTS_SECRET); they are never stored in the repository.
 */
@Component
public class WompiClient {

    private final String publicKey;
    private final String integritySecret;
    private final String eventsSecret;
    private final RestClient http;

    public WompiClient(@Value("${canchaya.wompi.public-key:}") String publicKey,
                       @Value("${canchaya.wompi.integrity-secret:}") String integritySecret,
                       @Value("${canchaya.wompi.events-secret:}") String eventsSecret,
                       @Value("${canchaya.wompi.api-url:https://sandbox.wompi.co/v1}") String apiUrl) {
        this.publicKey = publicKey;
        this.integritySecret = integritySecret;
        this.eventsSecret = eventsSecret;
        this.http = RestClient.builder().baseUrl(apiUrl).build();
    }

    public boolean isConfigured() {
        return !publicKey.isBlank() && !integritySecret.isBlank();
    }

    public String publicKey() {
        return publicKey;
    }

    /** Integrity signature Wompi requires on the checkout: SHA-256(reference + cents + currency + secret). */
    public String integritySignature(String reference, long amountInCents, String currency) {
        return sha256(reference + amountInCents + currency + integritySecret);
    }

    /** Reads a transaction from Wompi (public endpoint, no private key needed). */
    public Optional<WompiTransaction> findTransaction(String transactionId) {
        try {
            JsonNode body = http.get().uri("/transactions/{id}", transactionId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> { })
                    .body(JsonNode.class);
            JsonNode data = body == null ? null : body.get("data");
            return data == null || data.isNull() ? Optional.empty() : Optional.of(WompiTransaction.from(data));
        } catch (RestClientException e) {
            throw new IllegalStateException("Could not reach Wompi: " + e.getMessage(), e);
        }
    }

    /**
     * Checks the checksum of a webhook event: SHA-256 of the values listed in
     * signature.properties (read from "data"), then the timestamp, then the events secret.
     */
    public boolean isAuthentic(JsonNode event) {
        if (eventsSecret.isBlank() || event == null) {
            return false;
        }
        JsonNode signature = event.path("signature");
        StringBuilder payload = new StringBuilder();
        for (JsonNode property : signature.path("properties")) {
            JsonNode value = event.path("data");
            for (String key : property.asText().split("\\.")) {
                value = value.path(key);
            }
            payload.append(value.asText());
        }
        payload.append(event.path("timestamp").asText()).append(eventsSecret);
        return MessageDigest.isEqual(
                sha256(payload.toString()).getBytes(StandardCharsets.UTF_8),
                signature.path("checksum").asText().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
