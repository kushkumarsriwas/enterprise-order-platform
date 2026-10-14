package com.enterprise.notification.email;

/**
 * Sends one e-mail. The platform ships with {@link LoggingEmailSender} (prints the e-mail to the log); an SMTP or
 * Amazon SES implementation can replace it without changing the notification service.
 */
public interface EmailSender {

    void send(String to, String subject, String body);
}
