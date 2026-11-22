package com.canchaya.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default sender for development: just writes the message to the log. */
@Component
@ConditionalOnProperty(name = "canchaya.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LogNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("[MAIL to={}] {} - {}", to, subject, body);
    }
}
