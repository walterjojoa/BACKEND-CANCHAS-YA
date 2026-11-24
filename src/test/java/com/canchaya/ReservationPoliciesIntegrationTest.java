package com.canchaya;

import com.canchaya.payment.WompiClient;
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

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Production-like rules: no simulated payments, unpaid reservations expire at once
 * (timeout 0) and reference fields cannot be booked.
 */
@SpringBootTest(properties = {
        // Own in-memory database so the seeded Pasto fields don't leak into the other test classes
        "spring.datasource.url=jdbc:h2:mem:policies;DB_CLOSE_DELAY=-1",
        "canchaya.seed=true",
        "canchaya.payments.simulated-enabled=false",
        "canchaya.reservations.payment-timeout-minutes=0",
        "canchaya.reservations.reference-bookable=false"})
@AutoConfigureMockMvc
class ReservationPoliciesIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockBean
    private WompiClient wompi;

    @Test
    void simulatedPaymentsAreNotAvailable() throws Exception {
        String player = register("Sim Player", "sim-player@test.co", "PLAYER");
        long reservationId = book(player, newField("Sim Arena"), 18, 19);

        mvc.perform(post("/api/reservations/" + reservationId + "/payments").header("Authorization", bearer(player))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"NEQUI\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unpaidReservationReleasesItsSlot() throws Exception {
        String first = register("First", "expiry-first@test.co", "PLAYER");
        String second = register("Second", "expiry-second@test.co", "PLAYER");
        long fieldId = newField("Expiry Arena");
        long firstReservation = book(first, fieldId, 19, 21);
        Thread.sleep(5); // timeout 0: it expires as soon as any time passes

        // The expired slot shows as free and someone else can take it
        mvc.perform(get("/api/fields/" + fieldId + "/availability").param("date", date().toString()))
                .andExpect(jsonPath("$[11].hour").value(19))
                .andExpect(jsonPath("$[11].available").value(true));
        book(second, fieldId, 20, 22);

        // The first reservation was cancelled and can no longer be paid
        mvc.perform(get("/api/reservations/" + firstReservation).header("Authorization", bearer(first)))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        when(wompi.isConfigured()).thenReturn(true);
        mvc.perform(post("/api/reservations/" + firstReservation + "/payments/wompi")
                        .header("Authorization", bearer(first)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void referenceFieldsCannotBeBooked() throws Exception {
        String player = register("Ref Player", "ref-player@test.co", "PLAYER");
        JsonNode reference = null;
        for (JsonNode f : body(mvc.perform(get("/api/fields")).andReturn().getResponse().getContentAsString())) {
            if (f.get("referenceListing").asBoolean()) {
                reference = f;
                break;
            }
        }
        long fieldId = reference.get("id").asLong();

        mvc.perform(get("/api/fields/" + fieldId + "/availability").param("date", date().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].available", everyItem(is(false))));
        mvc.perform(post("/api/reservations").header("Authorization", bearer(player))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldId\":" + fieldId + ",\"date\":\"" + date() + "\",\"startHour\":18,\"endHour\":19}"))
                .andExpect(status().isBadRequest());
    }

    private long newField(String name) throws Exception {
        String owner = register(name + " Owner", name.replace(' ', '-').toLowerCase() + "@test.co", "OWNER");
        long fieldId = body(mvc.perform(post("/api/fields").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"address\":\"Pasto\",\"pricePerHour\":60000}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/fields/" + fieldId + "/schedules").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfWeek\":\"" + date().getDayOfWeek() + "\",\"openingHour\":8,\"closingHour\":22}"))
                .andExpect(status().isOk());
        return fieldId;
    }

    private long book(String token, long fieldId, int start, int end) throws Exception {
        return body(mvc.perform(post("/api/reservations").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fieldId\":" + fieldId + ",\"date\":\"" + date() + "\",\"startHour\":" + start
                                + ",\"endHour\":" + end + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private static LocalDate date() {
        return LocalDate.now(ZoneId.of("America/Bogota")).plusDays(5);
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
