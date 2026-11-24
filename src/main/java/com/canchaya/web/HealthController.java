package com.canchaya.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Endpoint de salud: lo usa Render para saber si el servicio está vivo y para despertarlo. */
@RestController
public class HealthController {

    /** GET /api/health → {"status":"UP","service":"canchaya-backend"}. Público. */
    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "canchaya-backend");
    }
}
