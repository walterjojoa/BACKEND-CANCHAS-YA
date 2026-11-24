package com.canchaya.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** Configuración general de la aplicación (beans compartidos). */
@Configuration
public class AppConfig {

    /**
     * Reloj de la aplicación. Las canchas están en Pasto, así que "ahora" siempre es la hora
     * de Colombia, sin importar dónde corra el servidor (Render está en Ohio, EE. UU.).
     */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("America/Bogota"));
    }
}
