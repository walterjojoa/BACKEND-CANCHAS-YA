package com.canchaya;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Authentication is done with our own JWT filter, so Spring's default in-memory user is disabled.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class CanchaYaApplication {

    public static void main(String[] args) {
        SpringApplication.run(CanchaYaApplication.class, args);
    }
}
