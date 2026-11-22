package com.canchaya.config;

import com.canchaya.domain.Zone;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the real Pasto fields file that DataSeeder (and the database repo) load. */
class PastoFieldsCsvTest {

    @Test
    void splitCsvKeepsCommasInsideQuotes() {
        assertThat(DataSeeder.splitCsv("Monumental,\"Cl. 16 #35-26, Pasto\",1.2,-77.2,NORTH,8,22,estimated"))
                .containsExactly("Monumental", "Cl. 16 #35-26, Pasto", "1.2", "-77.2", "NORTH", "8", "22", "estimated");
    }

    @Test
    void everyPastoFieldHasValidCoordinatesZoneAndHours() throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("data/pasto_fields.csv").getInputStream(), StandardCharsets.UTF_8))) {
            List<String> lines = reader.lines().skip(1).filter(l -> !l.isBlank()).toList();

            assertThat(lines).hasSize(38);
            for (String line : lines) {
                List<String> c = DataSeeder.splitCsv(line);
                assertThat(c).hasSize(8);
                double lat = Double.parseDouble(c.get(2));
                double lng = Double.parseDouble(c.get(3));
                // Pasto bounding box (approx.)
                assertThat(lat).as(c.get(0)).isBetween(1.15, 1.25);
                assertThat(lng).as(c.get(0)).isBetween(-77.32, -77.22);
                assertThat(Zone.valueOf(c.get(4))).isNotNull();
                assertThat(Integer.parseInt(c.get(6))).isGreaterThan(Integer.parseInt(c.get(5)));
            }
        }
    }
}
