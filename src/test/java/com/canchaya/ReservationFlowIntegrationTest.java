package com.canchaya;

import com.canchaya.payment.WompiClient;
import com.canchaya.payment.WompiTransaction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end flow through the real HTTP layer: owner publishes, player books and pays. */
@SpringBootTest(properties = "canchaya.seed=false")
@AutoConfigureMockMvc
class ReservationFlowIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockBean
    private WompiClient wompi;

    @Test
    void ownerPublishesFieldAndPlayerBooksAndPays() throws Exception {
        String ownerToken = register("Owner", "owner-it@test.co", "OWNER");
        String playerToken = register("Player", "player-it@test.co", "PLAYER");
        LocalDate date = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(3);

        // Owner publishes a field and its opening hours
        JsonNode field = body(mvc.perform(post("/api/fields").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"El Ejido","address":"Pasto","pricePerHour":80000,"zone":"CENTER"}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long fieldId = field.get("id").asLong();

        mvc.perform(post("/api/fields/" + fieldId + "/schedules").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":\"" + date.getDayOfWeek() + "\",\"openingHour\":8,\"closingHour\":22}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedules.length()").value(1));

        // A player cannot publish fields
        mvc.perform(post("/api/fields").header("Authorization", bearer(playerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"address\":\"Pasto\",\"pricePerHour\":1}"))
                .andExpect(status().isForbidden());

        // Player books 19:00-21:00
        String reservation = "{\"fieldId\":" + fieldId + ",\"date\":\"" + date + "\",\"startHour\":19,\"endHour\":21}";
        JsonNode created = body(mvc.perform(post("/api/reservations").header("Authorization", bearer(playerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(reservation))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(160000))
                .andExpect(jsonPath("$.depositAmount").value(48000))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andReturn().getResponse().getContentAsString());
        long reservationId = created.get("id").asLong();

        // Same slot again -> 409
        mvc.perform(post("/api/reservations").header("Authorization", bearer(playerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldId\":" + fieldId + ",\"date\":\"" + date + "\",\"startHour\":20,\"endHour\":22}"))
                .andExpect(status().isConflict());

        // Availability shows 19 and 20 as taken
        mvc.perform(get("/api/fields/" + fieldId + "/availability").param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[11].hour").value(19))
                .andExpect(jsonPath("$[11].available").value(false))
                .andExpect(jsonPath("$[13].available").value(true));

        // Pay the deposit with Nequi -> reservation confirmed
        mvc.perform(post("/api/reservations/" + reservationId + "/payments").header("Authorization", bearer(playerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"NEQUI\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").isNumber())
                .andExpect(jsonPath("$.amount").value(48000))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reservationStatus").value("CONFIRMED"));

        // Owner sees the reservation
        mvc.perform(get("/api/owner/reservations").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(reservationId));
    }

    @Test
    void playerPaysDepositThroughWompiCheckout() throws Exception {
        String ownerToken = register("Wompi Owner", "wompi-owner@test.co", "OWNER");
        String playerToken = register("Wompi Player", "wompi-player@test.co", "PLAYER");
        String otherToken = register("Other Player", "wompi-other@test.co", "PLAYER");
        LocalDate date = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(4);
        long fieldId = body(mvc.perform(post("/api/fields").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Wompi Arena\",\"address\":\"Pasto\",\"pricePerHour\":50000}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/fields/" + fieldId + "/schedules").header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dayOfWeek\":\"" + date.getDayOfWeek() + "\",\"openingHour\":8,\"closingHour\":22}"));
        long reservationId = body(mvc.perform(post("/api/reservations").header("Authorization", bearer(playerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldId\":" + fieldId + ",\"date\":\"" + date + "\",\"startHour\":10,\"endHour\":12}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();

        when(wompi.isConfigured()).thenReturn(true);
        when(wompi.publicKey()).thenReturn("pub_test_x");
        when(wompi.integritySignature(anyString(), anyLong(), anyString())).thenReturn("signed");

        // Step 1: signed checkout data (deposit 30 % of 100 000 = 30 000 pesos = 3 000 000 cents)
        String reference = body(mvc.perform(post("/api/reservations/" + reservationId + "/payments/wompi")
                        .header("Authorization", bearer(playerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountInCents").value(3_000_000))
                .andExpect(jsonPath("$.currency").value("COP"))
                .andExpect(jsonPath("$.integritySignature").value("signed"))
                .andReturn().getResponse().getContentAsString()).get("reference").asText();

        // Still processing at the bank -> PENDING, nothing recorded
        when(wompi.findTransaction("tx-1")).thenReturn(Optional.of(
                new WompiTransaction("tx-1", reference, 3_000_000, "COP", "PENDING", "PSE")));
        confirm(playerToken, "tx-1").andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.reservationStatus").value("PENDING_PAYMENT"));

        // Another player cannot confirm someone else's payment
        confirm(otherToken, "tx-1").andExpect(status().isForbidden());

        // Approved -> reservation confirmed; confirming twice is harmless
        when(wompi.findTransaction("tx-1")).thenReturn(Optional.of(
                new WompiTransaction("tx-1", reference, 3_000_000, "COP", "APPROVED", "PSE")));
        confirm(playerToken, "tx-1").andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.paymentMethodType").value("PSE"))
                .andExpect(jsonPath("$.reservationStatus").value("CONFIRMED"));
        confirm(playerToken, "tx-1").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));

        // Webhook with a bad checksum is rejected
        when(wompi.isAuthentic(any())).thenReturn(false);
        mvc.perform(post("/api/payments/wompi/events").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions confirm(String token, String transactionId) throws Exception {
        return mvc.perform(post("/api/payments/wompi/confirm").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"transactionId\":\"" + transactionId + "\"}"));
    }

    @Test
    void fieldsCanBeFilteredByZone() throws Exception {
        String ownerToken = register("Zone Owner", "zone-owner@test.co", "OWNER");
        mvc.perform(post("/api/fields").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cancha Zona Sur\",\"address\":\"Pasto\",\"pricePerHour\":70000,\"zone\":\"SOUTH\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.zone").value("SOUTH"))
                .andExpect(jsonPath("$.referenceListing").value(false));

        mvc.perform(get("/api/fields").param("zone", "SOUTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Cancha Zona Sur')]").exists());
        mvc.perform(get("/api/fields").param("zone", "NORTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Cancha Zona Sur')]").doesNotExist());
        mvc.perform(get("/api/fields").param("zone", "MARS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nearbyReturnsClosestFieldsFirst() throws Exception {
        String ownerToken = register("Near Owner", "near-owner@test.co", "OWNER");
        for (String body : new String[]{
                "{\"name\":\"Lejana\",\"address\":\"Pasto\",\"latitude\":1.16539,\"longitude\":-77.29776,\"pricePerHour\":70000}",
                "{\"name\":\"Cercana\",\"address\":\"Pasto\",\"latitude\":1.21400,\"longitude\":-77.28100,\"pricePerHour\":70000}"}) {
            mvc.perform(post("/api/fields").header("Authorization", bearer(ownerToken))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        }

        mvc.perform(get("/api/fields/nearby").param("lat", "1.21361").param("lng", "-77.28111").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].field.name").value("Cercana"))
                .andExpect(jsonPath("$[0].distanceKm").value(org.hamcrest.Matchers.lessThan(0.1)));
        mvc.perform(get("/api/fields/nearby").param("lat", "200").param("lng", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void protectedEndpointsRequireToken() throws Exception {
        mvc.perform(get("/api/reservations/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/fields")).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        register("Someone", "someone@test.co", "PLAYER");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"someone@test.co\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String register(String name, String email, String role) throws Exception {
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + email
                                + "\",\"password\":\"secret123\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return body(response).get("token").asText();
    }

    private JsonNode body(String content) throws Exception {
        return json.readTree(content);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
