package com.canchaya;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;

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
