package com.canchaya.notification;

/**
 * Canal por el que se entrega un mensaje al usuario.
 * <p>
 * GRASP Variaciones Protegidas: CÓMO le llega un mensaje al usuario (log en consola hoy,
 * correo SMTP en producción, quizá WhatsApp mañana) es un punto de cambio.
 * Esta interfaz estable protege al resto del sistema de ese cambio.
 */
public interface NotificationSender {

    /** Envía un mensaje con asunto y cuerpo al correo indicado. */
    void send(String to, String subject, String body);
}
