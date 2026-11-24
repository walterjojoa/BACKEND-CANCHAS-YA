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
 * Carga usuarios de prueba y las canchas sintéticas reales de Pasto cuando la base de datos
 * está vacía (canchaya.seed=true, se usa con la base H2 en memoria de desarrollo).
 * <p>
 * Las canchas salen de data/pasto_fields.csv, el mismo archivo que usa el repositorio de la
 * base de datos (BASE-DE-DATOS-CANCHAS-YA). Son negocios reales tomados de Google Maps que
 * NO se han unido a CanchaYa, así que son canchas "de referencia": su precio y horario son
 * estimados y pertenecen a una cuenta "referencias" en la que nadie puede iniciar sesión.
 */
@Component
@ConditionalOnProperty(name = "canchaya.seed", havingValue = "true") // solo si seed=true
public class DataSeeder implements CommandLineRunner {              // CommandLineRunner: corre al arrancar la app

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String FIELDS_CSV = "data/pasto_fields.csv";
    /** Precio estimado por hora para las canchas de referencia. */
    private static final BigDecimal ESTIMATED_PRICE = new BigDecimal("80000");
    /** No es un hash BCrypt, así que la cuenta de referencia nunca puede iniciar sesión. */
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
        // Si ya hay usuarios, la BD ya tiene datos: no se carga nada.
        if (users.count() > 0) {
            return;
        }
        // Todos los usuarios demo usan la misma clave: canchaya123.
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

    /** Lee el CSV y crea una cancha de referencia por cada fila, con el mismo horario todos los días. */
    private List<Field> readPastoFields(User reference) {
        List<Field> result = new ArrayList<>();
        // try-with-resources: el archivo se cierra solo al terminar.
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(FIELDS_CSV).getInputStream(), StandardCharsets.UTF_8))) {
            reader.readLine(); // encabezado: name,address,latitude,longitude,zone,opening_hour,closing_hour,hours_source
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
                    field.setSchedule(day, opening, closing);   // Creador: la cancha construye sus horarios
                }
                result.add(field);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + FIELDS_CSV, e);
        }
        return result;
    }

    /**
     * Separador de CSV mínimo: respeta las comas dentro de comillas dobles
     * (direcciones como "Cl. 16 #35-26, Pasto") y "" como comilla escapada.
     */
    static List<String> splitCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false; // ¿estamos dentro de comillas?
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"'); // "" dentro de comillas = una comilla literal
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                cells.add(cell.toString()); // fin de la celda
                cell.setLength(0);
            } else {
                cell.append(ch);
            }
        }
        cells.add(cell.toString()); // última celda
        return cells;
    }
}
