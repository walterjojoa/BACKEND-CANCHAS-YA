package com.canchaya;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Punto de entrada del backend de CanchaYa Pasto.
 * @SpringBootApplication activa la configuración automática y busca todos los componentes
 * (@RestController, @Service, @Component...) dentro del paquete com.canchaya.
 */
// La autenticación se hace con nuestro propio filtro JWT, así que se desactiva el usuario en memoria que trae Spring por defecto.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class CanchaYaApplication {

    /** Arranca el servidor web embebido (Tomcat) en el puerto 8080 o en el que indique la variable PORT. */
    public static void main(String[] args) {
        SpringApplication.run(CanchaYaApplication.class, args);
    }
}
