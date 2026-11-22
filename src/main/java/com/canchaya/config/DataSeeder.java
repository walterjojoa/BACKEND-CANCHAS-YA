package com.canchaya.config;

import com.canchaya.domain.Field;
import com.canchaya.domain.Role;
import com.canchaya.domain.User;
import com.canchaya.repository.FieldRepository;
import com.canchaya.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;

/** Loads demo users and fields in Pasto when the database is empty (canchaya.seed=true). */
@Component
@ConditionalOnProperty(name = "canchaya.seed", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

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
        User owner = users.save(new User("Carlos Owner", "owner@canchaya.co", password, Role.OWNER));
        users.save(new User("Andres Player", "player@canchaya.co", password, Role.PLAYER));

        Field ejido = new Field("Sintetica El Ejido", "Cra. 27 #10-35, Pasto", 1.2089, -77.2793,
                new BigDecimal("80000"), owner);
        Field bombonera = new Field("La Bombonera Pasto", "Av. Panamericana #18-20, Pasto", 1.2213, -77.2846,
                new BigDecimal("90000"), owner);
        for (DayOfWeek day : DayOfWeek.values()) {
            ejido.setSchedule(day, 8, 22);
            bombonera.setSchedule(day, day == DayOfWeek.SUNDAY ? 9 : 14, 23);
        }
        fields.save(ejido);
        fields.save(bombonera);
        log.info("Demo data loaded: owner@canchaya.co / player@canchaya.co (password: canchaya123)");
    }
}
