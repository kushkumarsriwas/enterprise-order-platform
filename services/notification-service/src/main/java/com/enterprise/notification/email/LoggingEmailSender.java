package com.enterprise.notification.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Development / demo e-mail channel: the e-mail is written to the log instead of a mail server. */
@Component
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    private final String from;

    public LoggingEmailSender(@Value("${app.mail.from:Nexora <no-reply@nexora.ae>}") String from) {
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        log.info("E-mail from {} to {} | {} | {}", from, mask(to), subject, body);
    }

    /** k***@example.com - addresses are personal data and should not appear in full in the logs. */
    static String mask(String address) {
        int at = address.indexOf('@');
        return at <= 1 ? "***" + address.substring(Math.max(at, 0)) : address.charAt(0) + "***" + address.substring(at);
    }
}
