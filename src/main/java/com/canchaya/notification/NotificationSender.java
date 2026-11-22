package com.canchaya.notification;

/**
 * GRASP Protected Variations: HOW a message reaches the user (console log today,
 * SMTP e-mail in production, maybe WhatsApp tomorrow) is a point of change.
 * This stable interface protects the rest of the system from that change.
 */
public interface NotificationSender {

    void send(String to, String subject, String body);
}
