package com.canchaya.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Emisor por defecto para desarrollo: solo escribe el mensaje en el log (consola).
 * Se activa si canchaya.mail.enabled=false o si la propiedad no existe (matchIfMissing).
 */
@Component
@ConditionalOnProperty(name = "canchaya.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LogNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationSender.class);

    /** "Envía" el correo imprimiéndolo en el log. */
    @Override
    public void send(String to, String subject, String body) {
        log.info("[MAIL to={}] {} - {}", to, subject, body);
    }
}
