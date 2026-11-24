package com.canchaya.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Emisor de producción: correo real por SMTP.
 * Solo se activa con canchaya.mail.enabled=true (si no, se usa LogNotificationSender).
 */
@Component
@ConditionalOnProperty(name = "canchaya.mail.enabled", havingValue = "true")
public class SmtpNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpNotificationSender.class);

    private final JavaMailSender mailSender; // lo configura Spring con spring.mail.* (host, usuario, clave)
    private final String from;               // remitente de los correos

    public SmtpNotificationSender(JavaMailSender mailSender,
                                  @Value("${canchaya.mail.from:no-reply@canchaya.co}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    /** Arma y envía un correo de texto plano. */
    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (MailException e) {
            // Un correo que falla nunca debe deshacer una reserva o un pago: solo se registra el aviso.
            log.warn("Could not send e-mail to {}: {}", to, e.getMessage());
        }
    }
}
