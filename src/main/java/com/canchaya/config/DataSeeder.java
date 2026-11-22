package com.canchaya.config;

import com.canchaya.domain.Field;
import com.canchaya.domain.Role;
import com.canchaya.domain.User;
import com.canchaya.domain.Zone;
import com.canchaya.repository.FieldRepository;
import com.canchaya.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads demo users and the real synthetic fields of Pasto when the database is empty
 * (canchaya.seed=true, used with the in-memory H2 database).
 * <p>
 * The fields come from data/pasto_fields.csv, the same file the database repository
 * (BASE-DE-DATOS-CANCHAS-YA) uses. They are real businesses taken from public Google Maps
 * listings that have NOT joined CanchaYa, so they are reference listings: their price and
 * opening hours are estimates, and they belong to a "reference" account nobody can log into.
 */
@Component
@ConditionalOnProperty(name = "canchaya.seed", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String FIELDS_CSV = "data/pasto_fields.csv";
    private static final BigDecimal ESTIMATED_PRICE = new BigDecimal("80000");
    /** Not a BCrypt hash, so the reference account can never log in. */
    private static final String LOGIN_DISABLED = "!login-disabled";

    private final UserRepository users;
    private final FieldRepository fields;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository users, FieldRepository fields, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.fields = fields;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.count() > 0) {
            return;
        }
        String password = passwordEncoder.encode("canchaya123");
        User reference = users.save(new User("CanchaYa - Canchas de referencia", "referencias@canchaya.co",
                LOGIN_DISABLED, Role.OWNER));
        users.save(new User("Carlos Owner", "owner@canchaya.co", password, Role.OWNER));
        users.save(new User("Andres Player", "player@canchaya.co", password, Role.PLAYER));
        users.save(new User("Laura Player", "laura@canchaya.co", password, Role.PLAYER));
        users.save(new User("Mateo Player", "mateo@canchaya.co", password, Role.PLAYER));
        users.save(new User("Admin CanchaYa", "admin@canchaya.co", password, Role.ADMIN));

        List<Field> pastoFields = readPastoFields(reference);
        fields.saveAll(pastoFields);
        log.info("Demo data loaded: {} real fields of Pasto; users player@/owner@canchaya.co (password: canchaya123)",
                pastoFields.size());
    }

    private List<Field> readPastoFields(User reference) {
        List<Field> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(FIELDS_CSV).getInputStream(), StandardCharsets.UTF_8))) {
            reader.readLine(); // header: name,address,latitude,longitude,zone,opening_hour,closing_hour,hours_source
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> c = splitCsv(line);
                Field field = new Field(c.get(0), c.get(1), Double.valueOf(c.get(2)), Double.valueOf(c.get(3)),
                        ESTIMATED_PRICE, reference, Zone.valueOf(c.get(4))).asReferenceListing();
                int opening = Integer.parseInt(c.get(5));
                int closing = Integer.parseInt(c.get(6));
                for (DayOfWeek day : DayOfWeek.values()) {
                    field.setSchedule(day, opening, closing);   // Creator: the field builds its schedules
                }
                result.add(field);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + FIELDS_CSV, e);
        }
        return result;
    }

    /** Minimal CSV split: commas inside double quotes are kept (addresses like "Cl. 16 #35-26, Pasto"). */
    static List<String> splitCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(ch);
            }
        }
        cells.add(cell.toString());
        return cells;
    }
}
