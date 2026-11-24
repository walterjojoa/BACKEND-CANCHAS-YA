package com.canchaya.payment;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Cliente de bajo nivel para la API de Wompi (sandbox por defecto, así que no se mueve dinero real).
 * Habla el idioma de Wompi: montos en centavos, firmas SHA-256 y JSON en snake_case.
 * Solo {@link com.canchaya.service.WompiCheckoutService} lo usa.
 * <p>
 * Las llaves vienen de variables de entorno (WOMPI_PUBLIC_KEY, WOMPI_INTEGRITY_SECRET,
 * WOMPI_EVENTS_SECRET); nunca se guardan en el repositorio.
 */
@Component
public class WompiClient {

    private static final Logger log = LoggerFactory.getLogger(WompiClient.class);

    private final String publicKey;       // llave pública: la ve el navegador
    private final String integritySecret; // secreto para firmar el monto del checkout
    private final String eventsSecret;    // secreto para verificar los webhooks
    private final RestClient http;        // cliente HTTP de Spring apuntando a la API de Wompi

    /** @Value lee cada propiedad; si no está definida, queda vacía (":" = valor por defecto). */
    public WompiClient(@Value("${canchaya.wompi.public-key:}") String publicKey,
                       @Value("${canchaya.wompi.integrity-secret:}") String integritySecret,
                       @Value("${canchaya.wompi.events-secret:}") String eventsSecret,
                       @Value("${canchaya.wompi.api-url:https://sandbox.wompi.co/v1}") String apiUrl) {
        // strip(): al pegar una llave en un panel es fácil que se cuele un espacio o salto de línea,
        // y con un solo carácter de más la firma ya no coincide con la que calcula Wompi.
        this.publicKey = publicKey.strip();
        this.integritySecret = integritySecret.strip();
        this.eventsSecret = eventsSecret.strip();
        this.http = RestClient.builder().baseUrl(apiUrl).build();
        logKeyKinds(apiUrl);
    }

    /**
     * Deja en el log de qué tipo es cada llave (pruebas o producción) y su largo, SIN mostrar los secretos.
     * Sirve para detectar llaves de ambientes mezclados, que Wompi rechaza con "La firma es inválida".
     */
    private void logKeyKinds(String apiUrl) {
        String pubKind = kind(publicKey, "pub_test_", "pub_prod_");
        String integrityKind = kind(integritySecret, "test_integrity_", "prod_integrity_");
        String eventsKind = kind(eventsSecret, "test_events_", "prod_events_");
        log.info("Wompi: api={} | llave publica={} | secreto integridad={} (largo {}) | secreto eventos={} (largo {})",
                apiUrl, pubKind, integrityKind, integritySecret.length(), eventsKind, eventsSecret.length());
        if (!pubKind.equals(integrityKind) && !publicKey.isBlank() && !integritySecret.isBlank()) {
            log.warn("Wompi: la llave publica es de {} pero el secreto de integridad es de {}; Wompi rechazara la firma",
                    pubKind, integrityKind);
        }
    }

    static String kind(String key, String testPrefix, String prodPrefix) {
        if (key.isBlank()) return "VACIA";
        if (key.startsWith(testPrefix)) return "PRUEBAS";
        if (key.startsWith(prodPrefix)) return "PRODUCCION";
        return "FORMATO DESCONOCIDO";
    }

    /** true si hay llave pública y secreto de integridad (lo mínimo para abrir el checkout). */
    public boolean isConfigured() {
        return !publicKey.isBlank() && !integritySecret.isBlank();
    }

    public String publicKey() {
        return publicKey;
    }

    /**
     * Firma de integridad que Wompi exige en el checkout: SHA-256(referencia + centavos + moneda + secreto).
     * Como el secreto solo lo conoce el servidor, nadie puede cambiar el monto en el navegador.
     */
    public String integritySignature(String reference, long amountInCents, String currency) {
        return sha256(reference + amountInCents + currency + integritySecret);
    }

    /** Consulta una transacción en Wompi (endpoint público, no necesita llave privada). */
    public Optional<WompiTransaction> findTransaction(String transactionId) {
        try {
            JsonNode body = http.get().uri("/transactions/{id}", transactionId)
                    .retrieve()
                    // Un 4xx (p. ej. 404) no lanza excepción: se trata abajo como "no encontrada".
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> { })
                    .body(JsonNode.class);
            JsonNode data = body == null ? null : body.get("data");
            return data == null || data.isNull() ? Optional.empty() : Optional.of(WompiTransaction.from(data));
        } catch (RestClientException e) {
            // Error de red o 5xx: Wompi no respondió.
            throw new IllegalStateException("Could not reach Wompi: " + e.getMessage(), e);
        }
    }

    /**
     * Verifica el checksum de un evento del webhook: SHA-256 de los valores listados en
     * signature.properties (leídos de "data"), luego el timestamp y luego el secreto de eventos.
     * Así se comprueba que el evento lo envió Wompi y no alguien que conoce la URL.
     */
    public boolean isAuthentic(JsonNode event) {
        if (eventsSecret.isBlank() || event == null) {
            return false;
        }
        JsonNode signature = event.path("signature");
        StringBuilder payload = new StringBuilder();
        // Cada propiedad es una ruta como "transaction.id": se recorre el JSON por partes.
        for (JsonNode property : signature.path("properties")) {
            JsonNode value = event.path("data");
            for (String key : property.asText().split("\\.")) {
                value = value.path(key);
            }
            payload.append(value.asText());
        }
        payload.append(event.path("timestamp").asText()).append(eventsSecret);
        // MessageDigest.isEqual compara en tiempo constante (evita ataques por tiempo de respuesta).
        return MessageDigest.isEqual(
                sha256(payload.toString()).getBytes(StandardCharsets.UTF_8),
                signature.path("checksum").asText().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    /** Calcula el hash SHA-256 de un texto y lo devuelve en hexadecimal. */
    static String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
